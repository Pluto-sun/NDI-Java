package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_progress")
public class ProjectProgressEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "progress_content", nullable = false)
    private String progressContent;

    @Column(name = "next_action", nullable = false)
    private String nextAction;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    protected ProjectProgressEntity() {
    }

    public static ProjectProgressEntity initial(
            UUID projectId,
            String progressContent,
            String nextAction,
            UUID actorId,
            Instant occurredAt
    ) {
        var entity = new ProjectProgressEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = projectId;
        entity.progressContent = progressContent;
        entity.nextAction = nextAction;
        entity.actorId = actorId;
        entity.occurredAt = occurredAt;
        return entity;
    }
}
