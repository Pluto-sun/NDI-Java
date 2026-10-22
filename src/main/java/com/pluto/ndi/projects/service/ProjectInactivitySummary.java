package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 通知中心按督办周期聚合展示，避免同一项目堆积重复提醒。 */
public record ProjectInactivitySummary(
        UUID id, UUID projectId, String projectCode, String projectName, String status,
        Instant lastActivityAt, Instant createdAt, Instant closedAt, boolean canForward, boolean unread,
        List<ProjectInactivityEvent> events
) {
}
