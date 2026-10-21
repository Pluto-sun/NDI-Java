package com.pluto.ndi.projects.repository;

import java.time.Instant;
import java.util.UUID;

/** 工作台动态的轻量查询行，不向前端暴露事件原始载荷。 */
public record ProjectActivitySourceRow(
        UUID id, UUID projectId, String projectCode, String projectName,
        String province, String city, String operatorName, Instant occurredAt,
        String eventType, String payload
) {
}
