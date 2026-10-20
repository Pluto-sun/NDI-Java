package com.pluto.ndi.projects.service;

/** 当前账号的项目审批待办数量。 */
public record ApprovalInboxCounts(long pendingCount, long overdueCount) { }
