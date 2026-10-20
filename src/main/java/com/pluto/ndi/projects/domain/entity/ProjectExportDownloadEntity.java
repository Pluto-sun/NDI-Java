package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_export_download")
public class ProjectExportDownloadEntity {

    @Id
    private UUID id;

    @Column(name = "export_id", nullable = false)
    private UUID exportId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ProjectExportDownloadEntity() {
    }

    public static ProjectExportDownloadEntity create(UUID exportId, UUID actorId, Instant occurredAt) {
        var event = new ProjectExportDownloadEntity();
        event.id = UUID.randomUUID();
        event.exportId = exportId;
        event.actorId = actorId;
        event.occurredAt = occurredAt;
        return event;
    }
}
