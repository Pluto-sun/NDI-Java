package com.pluto.ndi.projects.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 三类数量在同一只读事务内查询，不创建补偿通知，也不把失败当作零。 */
@Service
public class ProjectNotificationSummaryService {
    private final ProjectApprovalNotificationService notifications;
    private final ProjectInactivityService inactivity;
    private final ProjectApprovalInboxService approvals;

    public ProjectNotificationSummaryService(ProjectApprovalNotificationService notifications,
            ProjectInactivityService inactivity, ProjectApprovalInboxService approvals) {
        this.notifications = notifications; this.inactivity = inactivity; this.approvals = approvals;
    }

    @Transactional(readOnly = true)
    public ProjectNotificationCounts counts(UUID actorId) {
        long unread = notifications.unreadCount(actorId);
        long pendingCases = inactivity.pendingCount(actorId);
        long unreadCases = inactivity.unreadCount(actorId);
        return new ProjectNotificationCounts(unread + unreadCases, unread, pendingCases,
                approvals.counts(actorId).pendingCount(), unreadCases);
    }
}
