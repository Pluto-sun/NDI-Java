package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

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
    @SuppressWarnings("unchecked")
    void missingCurrentScopeReturnsEmptyWithoutQueryingHistoricApplications() {
        var actor = UUID.randomUUID();
        var identities = mock(IdentityDirectory.class);
        var repository = mock(ProjectApprovalRequestRepository.class);
        when(identities.findProjectActor(actor)).thenReturn(Optional.of(new ProjectActor(actor, null, null, true)));
        var service = new ProjectApprovalInboxService(identities, repository, Clock.systemUTC());
        assertThat(service.findSubmitted(actor, PageRequest.of(0, 20))).isEmpty();
        assertThat(service.findRecords(actor, null, PageRequest.of(0, 20))).isEmpty();
        verify(repository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void submittedUsesCurrentProjectScopeForEnabledActor() {
        var actorId = UUID.randomUUID();
        var page = PageRequest.of(0, 20);
        var identity = mock(IdentityDirectory.class);
        var requests = mock(ProjectApprovalRequestRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(
                new ProjectActor(actorId, UUID.randomUUID(), null, true)));
        when(identity.findReadableProjectScope(any())).thenReturn(Optional.of(
                new com.pluto.ndi.identity.service.ProjectDataScope(com.pluto.ndi.identity.service.ProjectDataScope.Level.NATIONAL, null)));
        when(requests.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<ProjectApprovalRequestEntity>(List.of()));
        var service = new ProjectApprovalInboxService(identity, requests,
                Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.findSubmitted(actorId, page).getContent()).isEmpty();
        verify(requests).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
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
        verify(requests, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
    }
}
