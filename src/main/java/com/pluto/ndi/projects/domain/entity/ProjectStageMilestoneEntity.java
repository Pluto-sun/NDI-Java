package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_stage_milestone")
public class ProjectStageMilestoneEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "stage_code", nullable = false)
    private String stageCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "description_text")
    private String descriptionText;

    protected ProjectStageMilestoneEntity() {
    }

    public static ProjectStageMilestoneEntity initial(UUID projectId, UUID actorId, Instant occurredAt) {
        var entity = new ProjectStageMilestoneEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = projectId;
        entity.stageCode = ProjectStage.SOLUTION_EXCHANGE.name();
        entity.occurredAt = occurredAt;
        entity.sourceType = "PROJECT_CREATED";
        entity.actorId = actorId;
        return entity;
    }
}
