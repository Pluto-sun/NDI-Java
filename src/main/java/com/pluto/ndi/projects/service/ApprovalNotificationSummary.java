package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalNotificationEntity;

/** 站内通知只读视图。 */
public record ApprovalNotificationSummary(
        UUID id,
        UUID requestId,
        ApprovalNotificationKind kind,
        Instant createdAt,
        Instant readAt
) {
    public static ApprovalNotificationSummary from(ProjectApprovalNotificationEntity notification) {
        return new ApprovalNotificationSummary(
                notification.id(), notification.requestId(), notification.kind(),
                notification.createdAt(), notification.readAt()
        );
    }
}
