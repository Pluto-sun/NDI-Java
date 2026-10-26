package com.pluto.ndi.projects.domain;

/** 审批站内通知的业务事件类型。 */
public enum ApprovalNotificationKind {
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    CONFLICT,
    REASSIGNED,
    OVERDUE
}
