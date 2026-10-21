package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
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

    @Column(name = "progress_date")
    private LocalDate progressDate;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "corrects_progress_id")
    private UUID correctsProgressId;

    @Column(name = "owner_name", length = 100)
    private String ownerName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    protected ProjectProgressEntity() {
    }

    public static ProjectProgressEntity initial(
            UUID projectId, String progressContent, String nextAction, String ownerName,
            UUID actorId, Instant occurredAt, LocalDate progressDate
    ) {
        return append(projectId, progressContent, nextAction, actorId, occurredAt,
                null, progressDate, ownerName, null);
    }

    public static ProjectProgressEntity append(
            UUID projectId, String progressContent, String nextAction, UUID actorId,
            Instant occurredAt, UUID correctsProgressId, LocalDate progressDate,
            String ownerName, LocalDate dueDate
    ) {
        var entity = new ProjectProgressEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = projectId;
        entity.progressContent = progressContent;
        entity.nextAction = nextAction;
        entity.actorId = actorId;
        entity.occurredAt = occurredAt;
        entity.correctsProgressId = correctsProgressId;
        entity.progressDate = progressDate;
        entity.ownerName = ownerName;
        entity.dueDate = dueDate;
        return entity;
    }

    public static ProjectProgressEntity imported(UUID projectId, String content,
            String nextAction, String ownerName, LocalDate dueDate, LocalDate progressDate,
            UUID actorId, Instant occurredAt) {
        return append(projectId, content, nextAction, actorId, occurredAt,
                null, progressDate, ownerName, dueDate);
    }

    public UUID id() { return id; }
    public UUID projectId() { return projectId; }
    public String progressContent() { return progressContent; }
    public String nextAction() { return nextAction; }
    public Instant occurredAt() { return occurredAt; }
    public LocalDate progressDate() { return progressDate; }
    public UUID actorId() { return actorId; }
    public UUID correctsProgressId() { return correctsProgressId; }
    public String ownerName() { return ownerName; }
    public LocalDate dueDate() { return dueDate; }
}
