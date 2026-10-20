package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ProjectApprovalInboxServiceTest {

    @Test
    void submittedOnlyReadsCurrentEnabledActor() {
        var actorId = UUID.randomUUID();
        var page = PageRequest.of(0, 20);
        var identity = mock(IdentityDirectory.class);
        var requests = mock(ProjectApprovalRequestRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(
                new ProjectActor(actorId, UUID.randomUUID(), null, true)));
        when(requests.findByInitiatorIdOrderBySubmittedAtDescIdDesc(actorId, page))
                .thenReturn(new PageImpl<ProjectApprovalRequestEntity>(List.of()));
        var service = new ProjectApprovalInboxService(identity, requests,
                Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.findSubmitted(actorId, page).getContent()).isEmpty();
        verify(requests).findByInitiatorIdOrderBySubmittedAtDescIdDesc(actorId, page);
    }

    @Test
    void submittedRejectsDisabledActorBeforeQuery() {
        var actorId = UUID.randomUUID();
        var page = PageRequest.of(0, 20);
        var identity = mock(IdentityDirectory.class);
        var requests = mock(ProjectApprovalRequestRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(
                new ProjectActor(actorId, UUID.randomUUID(), null, false)));
        var service = new ProjectApprovalInboxService(identity, requests, Clock.systemUTC());

        var failure = catchThrowable(() -> service.findSubmitted(actorId, page));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        verify(requests, never()).findByInitiatorIdOrderBySubmittedAtDescIdDesc(actorId, page);
    }
}
