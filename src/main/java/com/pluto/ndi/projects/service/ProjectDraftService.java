package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectDraftEntity;
import com.pluto.ndi.projects.repository.ProjectDraftRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 保存用户自己的未完成表单；草稿不进入项目主档及统计。 */
@Service
public class ProjectDraftService {
    private static final int MAX_PAYLOAD_LENGTH = 65_536;
    private final IdentityDirectory identity;
    private final ProjectDraftRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public ProjectDraftService(IdentityDirectory identity, ProjectDraftRepository repository,
            ObjectMapper json, Clock clock) {
        this.identity = identity;
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ProjectDraftSummary> findMine(UUID actorId, Pageable pageable) {
        requireCreator(actorId);
        return repository.findByOwnerIdOrderByUpdatedAtDescIdDesc(actorId, pageable).map(this::summary);
    }

    @Transactional
    public ProjectDraftSummary save(UUID actorId, UUID draftId, String title, JsonNode data) {
        requireCreator(actorId);
        if (data == null || !data.isObject()) throw new BusinessException(ProjectErrorCode.INVALID_DRAFT);
        var safeTitle = title == null || title.isBlank() ? "未命名项目草稿" : title.trim();
        if (safeTitle.length() > 200) throw new BusinessException(ProjectErrorCode.INVALID_DRAFT);
        String payload;
        try {
            payload = json.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_DRAFT);
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) throw new BusinessException(ProjectErrorCode.INVALID_DRAFT);
        var at = Instant.now(clock);
        var draft = draftId == null
                ? ProjectDraftEntity.create(actorId, safeTitle, payload, at)
                : repository.findByIdAndOwnerId(draftId, actorId)
                        .orElseThrow(() -> new BusinessException(ProjectErrorCode.DRAFT_NOT_FOUND));
        if (draftId != null) draft.update(safeTitle, payload, at);
        return summary(repository.save(draft));
    }

    @Transactional
    public void delete(UUID actorId, UUID draftId) {
        requireCreator(actorId);
        var draft = repository.findByIdAndOwnerId(draftId, actorId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.DRAFT_NOT_FOUND));
        repository.delete(draft);
    }

    private void requireCreator(UUID actorId) {
        var actor = identity.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN));
        if (!identity.canSaveProjectDraft(actor)) throw new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN);
    }

    private ProjectDraftSummary summary(ProjectDraftEntity draft) {
        try {
            return new ProjectDraftSummary(draft.id(), draft.title(), json.readTree(draft.payload()),
                    draft.createdAt(), draft.updatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("已保存草稿无法解析", exception);
        }
    }
}
