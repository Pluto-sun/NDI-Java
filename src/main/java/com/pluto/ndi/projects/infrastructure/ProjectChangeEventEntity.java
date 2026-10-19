package com.pluto.ndi.projects.infrastructure;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_change_event")
public class ProjectChangeEventEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ProjectChangeEventEntity() {
    }

    public static ProjectChangeEventEntity create(
            UUID projectId,
            long aggregateVersion,
            String payload,
            UUID actorId,
            Instant occurredAt
    ) {
        var entity = new ProjectChangeEventEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = projectId;
        entity.eventType = "PROJECT_CREATED";
        entity.aggregateVersion = aggregateVersion;
        entity.payload = payload;
        entity.actorId = actorId;
        entity.occurredAt = occurredAt;
        return entity;
    }
}
