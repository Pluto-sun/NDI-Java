package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_source_record")
public class ProjectSourceRecordEntity {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "source_system", nullable = false, length = 40) private String sourceSystem;
    @Column(name = "source_record_id", nullable = false, length = 100) private String sourceRecordId;
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "imported_at", nullable = false) private Instant importedAt;

    protected ProjectSourceRecordEntity() { }

    public static ProjectSourceRecordEntity of(UUID projectId, String system, String sourceId,
            String type, Instant at) {
        var item = new ProjectSourceRecordEntity();
        item.id = UUID.randomUUID();
        item.projectId = projectId;
        item.sourceSystem = system;
        item.sourceRecordId = sourceId;
        item.sourceType = type;
        item.importedAt = at;
        return item;
    }
}
