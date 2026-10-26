package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalNotificationEntity;
import com.pluto.ndi.projects.repository.ProjectApprovalNotificationRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审批状态与站内通知在同一事务中提交。 */
@Service
public class ProjectApprovalNotificationService {

    private final IdentityDirectory identityDirectory;
    private final ProjectApprovalNotificationRepository repository;
    private final Clock clock;

    public ProjectApprovalNotificationService(
            IdentityDirectory identityDirectory, ProjectApprovalNotificationRepository repository, Clock clock
    ) {
        this.identityDirectory = identityDirectory;
        this.repository = repository;
        this.clock = clock;
    }

    public void transferred(ProjectApprovalRequestEntity request, UUID previous, Instant at) {
        repository.readTransferred(request.id(), previous, at);
        repository.save(ProjectApprovalNotificationEntity.handover(request.id(), request.approverId(), at));
    }

    public void submitted(ProjectApprovalRequestEntity request, Instant at) {
        record(request.id(), request.approverId(), ApprovalNotificationKind.SUBMITTED, at);
    }

    public void finished(ProjectApprovalRequestEntity request, ApprovalNotificationKind kind, Instant at) {
        var recipient = kind == ApprovalNotificationKind.WITHDRAWN
                ? request.approverId() : request.initiatorId();
        record(request.id(), recipient, kind, at);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalNotificationSummary> findMine(UUID actorId, Pageable pageable) {
        return findMine(actorId, pageable, false);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalNotificationSummary> findMine(UUID actorId, Pageable pageable, boolean unreadOnly) {
        var actor = requireEnabled(actorId);
        var scope = identityDirectory.findReadableProjectScope(actor);
        var specification = visibleTo(actorId, scope);
        if (unreadOnly) specification = specification.and((notice, query, builder) -> builder.isNull(notice.get("readAt")));
        return repository.findAll(specification, org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(),
                        pageable.getPageSize(), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id")))
                .map(ApprovalNotificationSummary::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID actorId) {
        var actor = requireEnabled(actorId);
        return repository.count(visibleTo(actorId, identityDirectory.findReadableProjectScope(actor))
                .and((notice, query, builder) -> builder.isNull(notice.get("readAt"))));
    }

    private Specification<ProjectApprovalNotificationEntity> visibleTo(UUID actorId, Optional<ProjectDataScope> scope) {
        return (notice, query, builder) -> {
            var request = query.from(ProjectApprovalRequestEntity.class);
            var project = query.from(com.pluto.ndi.projects.domain.entity.ProjectEntity.class);
            var readable = scope.map(value -> com.pluto.ndi.projects.mapper.ProjectScopeCriteria.predicate(value, project, builder))
                    .orElseGet(builder::disjunction);
            return builder.and(builder.equal(notice.get("recipientId"), actorId),
                    builder.equal(notice.get("requestId"), request.get("id")), builder.equal(request.get("projectId"), project.get("id")),
                    builder.or(builder.equal(request.get("initiatorId"), actorId), readable));
        };
    }

    @Transactional
    public ApprovalNotificationSummary markRead(UUID actorId, UUID notificationId) {
        var actor = requireEnabled(actorId);
        var notification = repository.findByIdAndRecipientId(notificationId, actorId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOTIFICATION_NOT_FOUND));
        if (!repository.exists(visibleTo(actorId, identityDirectory.findReadableProjectScope(actor))
                .and((notice, query, builder) -> builder.equal(notice.get("id"), notificationId)))) {
            throw new BusinessException(ProjectErrorCode.NOTIFICATION_NOT_FOUND);
        }
        notification.markRead(Instant.now(clock));
        return ApprovalNotificationSummary.from(notification);
    }

    private void record(UUID requestId, UUID recipientId, ApprovalNotificationKind kind, Instant at) {
        repository.save(ProjectApprovalNotificationEntity.create(requestId, recipientId, kind, at));
    }

    private ProjectActor requireEnabled(UUID actorId) {
        return identityDirectory.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }
}
