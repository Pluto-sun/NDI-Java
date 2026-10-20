package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProjectHistoryServiceTest {

    @Test
    void checksProjectReadScopeBeforeReturningApprovalHistory() {
        var actorId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var page = PageRequest.of(0, 20);
        var access = mock(ProjectAccessService.class);
        var approvals = mock(ProjectApprovalRequestRepository.class);
        var changes = mock(ProjectChangeEventRepository.class);
        when(access.requireReadable(actorId, projectId))
                .thenThrow(new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var service = new ProjectHistoryService(access, approvals, changes);

        var failure = catchThrowable(() -> service.approvals(actorId, projectId, page));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        verify(approvals, never()).findByProjectIdOrderBySubmittedAtDescIdDesc(projectId, page);
    }

    @Test
    void checksProjectReadScopeBeforeReturningChanges() {
        var actorId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var page = PageRequest.of(0, 20);
        var access = mock(ProjectAccessService.class);
        var approvals = mock(ProjectApprovalRequestRepository.class);
        var changes = mock(ProjectChangeEventRepository.class);
        when(access.requireReadable(actorId, projectId))
                .thenThrow(new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var service = new ProjectHistoryService(access, approvals, changes);

        var failure = catchThrowable(() -> service.changes(actorId, projectId, page));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        verify(changes, never()).findByProjectIdOrderByOccurredAtDescIdDesc(projectId, page);
    }
}
