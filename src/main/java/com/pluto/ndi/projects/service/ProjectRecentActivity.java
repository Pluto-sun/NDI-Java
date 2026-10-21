package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

/** 项目工作台近 30 天动态摘要。 */
public record ProjectRecentActivity(
        UUID projectId, String projectCode, String projectName,
        String province, String city, String summary, String operatorName, Instant occurredAt
) {
}
