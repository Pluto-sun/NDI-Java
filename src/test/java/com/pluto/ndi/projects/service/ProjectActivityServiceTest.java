package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectActivitySourceRow;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProjectActivityServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T02:00:00Z");
    private final IdentityDirectory identities = mock(IdentityDirectory.class);
    private final ProjectChangeEventRepository events = mock(ProjectChangeEventRepository.class);
    private final ProjectProgressRepository progresses = mock(ProjectProgressRepository.class);
    private final ProjectActivityService service = new ProjectActivityService(
            identities, events, progresses, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void mergesScopedSourcesAndSummarizesApprovedChangesWithoutRawPayload() {
        var actorId = UUID.randomUUID();
        var provinceId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var actor = new ProjectActor(actorId, provinceId, null, true);
        var since = Instant.parse("2026-09-21T02:00:00Z");
        var page = PageRequest.of(0, 500);
        when(identities.findProjectActor(actorId)).thenReturn(Optional.of(actor));
        when(identities.findReadableProjectScope(actor)).thenReturn(Optional.of(
                new ProjectDataScope(ProjectDataScope.Level.PROVINCE, provinceId)));
        when(events.findRecentActivities(since, "PROVINCE", provinceId, java.util.Set.of(provinceId), page)).thenReturn(List.of(
                row(projectId, NOW.minusSeconds(10), "STAGE_ADVANCEMENT_APPROVED",
                        "{\"before\":{\"stageCode\":\"BIDDING\"},\"after\":{\"stageCode\":\"SIGNED\"}}")));
        when(progresses.findRecentActivities(since, "PROVINCE", provinceId, java.util.Set.of(provinceId), page)).thenReturn(List.of(
                row(projectId, NOW.minusSeconds(5), "PROGRESS_APPENDED", null)));

        var result = service.recent(actorId);

        assertThat(result).extracting(ProjectRecentActivity::summary)
                .containsExactly("追加项目进展", "阶段推进审批通过：签约");
        assertThat(result).allMatch(activity -> activity.projectId().equals(projectId));
        verify(events).findRecentActivities(since, "PROVINCE", provinceId, java.util.Set.of(provinceId), page);
        verify(progresses).findRecentActivities(since, "PROVINCE", provinceId, java.util.Set.of(provinceId), page);
    }

    @Test
    void disabledActorIsDeniedBeforeReadingActivity() {
        var actorId = UUID.randomUUID();
        when(identities.findProjectActor(actorId)).thenReturn(Optional.of(
                new ProjectActor(actorId, UUID.randomUUID(), null, false)));

        assertThatThrownBy(() -> service.recent(actorId))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN));
        verify(events, never()).findRecentActivities(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private ProjectActivitySourceRow row(UUID projectId, Instant occurredAt, String eventType, String payload) {
        return new ProjectActivitySourceRow(UUID.randomUUID(), projectId, "P-001", "测试项目",
                "广东省", "广州市", "测试人员", occurredAt, eventType, payload);
    }
}
