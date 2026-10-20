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
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectDraftRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProjectDraftServiceTest {

    @Test
    void nonCreatorCannotReadDrafts() {
        var actorId = UUID.randomUUID();
        var actor = new ProjectActor(actorId, UUID.randomUUID(), null, true);
        var identity = mock(IdentityDirectory.class);
        var repository = mock(ProjectDraftRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(actor));
        var service = new ProjectDraftService(identity, repository, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC));

        var failure = catchThrowable(() -> service.findMine(actorId, PageRequest.of(0, 20)));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.CREATION_FORBIDDEN);
        verify(repository, never()).findByOwnerIdOrderByUpdatedAtDescIdDesc(actorId, PageRequest.of(0, 20));
    }

    @Test
    void cannotUpdateAnotherUsersDraft() {
        var actorId = UUID.randomUUID();
        var draftId = UUID.randomUUID();
        var actor = new ProjectActor(actorId, UUID.randomUUID(), null, true);
        var identity = mock(IdentityDirectory.class);
        var repository = mock(ProjectDraftRepository.class);
        when(identity.findProjectActor(actorId)).thenReturn(Optional.of(actor));
        when(identity.canSaveProjectDraft(actor)).thenReturn(true);
        when(repository.findByIdAndOwnerId(draftId, actorId)).thenReturn(Optional.empty());
        var service = new ProjectDraftService(identity, repository, new ObjectMapper(), Clock.systemUTC());

        var failure = catchThrowable(() -> service.save(actorId, draftId, "我的草稿",
                new ObjectMapper().createObjectNode()));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.DRAFT_NOT_FOUND);
    }
}
