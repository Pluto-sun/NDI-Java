package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalNotificationEntity;
import com.pluto.ndi.projects.repository.ProjectApprovalNotificationRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        requireEnabled(actorId);
        return repository.findByRecipientIdOrderByCreatedAtDesc(actorId, pageable)
                .map(ApprovalNotificationSummary::from);
    }

    @Transactional
    public ApprovalNotificationSummary markRead(UUID actorId, UUID notificationId) {
        requireEnabled(actorId);
        var notification = repository.findByIdAndRecipientId(notificationId, actorId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(Instant.now(clock));
        return ApprovalNotificationSummary.from(notification);
    }

    private void record(UUID requestId, UUID recipientId, ApprovalNotificationKind kind, Instant at) {
        repository.save(ProjectApprovalNotificationEntity.create(requestId, recipientId, kind, at));
    }

    private void requireEnabled(UUID actorId) {
        identityDirectory.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }
}
