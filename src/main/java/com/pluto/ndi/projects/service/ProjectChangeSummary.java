package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;

/** 项目正式数据的变更审计摘要。 */
public record ProjectChangeSummary(
        UUID id, String eventType, long aggregateVersion, String payload,
        UUID actorId, Instant occurredAt
) {
    public static ProjectChangeSummary from(ProjectChangeEventEntity event) {
        return new ProjectChangeSummary(event.id(), event.eventType(), event.aggregateVersion(),
                event.payload(), event.actorId(), event.occurredAt());
    }
}
