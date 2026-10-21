package com.pluto.ndi.projects.service;

/** 消息总数不包含审批待办；已读消息不等于申请已处理。 */
public record ProjectNotificationCounts(long count, long approvalUnreadCount,
        long inactivityPendingCount, long pendingApprovalCount) { }
