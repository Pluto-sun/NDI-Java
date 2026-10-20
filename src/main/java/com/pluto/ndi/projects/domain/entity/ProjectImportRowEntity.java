package com.pluto.ndi.projects.domain.entity;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_import_row")
public class ProjectImportRowEntity {
    @Id private UUID id;
    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(name = "source_row_number", nullable = false) private int rowNumber;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "normalized_key", length = 512) private String normalizedKey;
    @Column(name = "row_payload", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String rowPayload;
    @Column(nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String errors;
    @Column(name = "project_id") private UUID projectId;

    protected ProjectImportRowEntity() { }

    public static ProjectImportRowEntity preview(UUID batchId, int number, String key,
            String payload, String errors, boolean valid) {
        var item = new ProjectImportRowEntity();
        item.id = UUID.randomUUID();
        item.batchId = batchId;
        item.rowNumber = number;
        item.normalizedKey = key;
        item.rowPayload = payload;
        item.errors = errors;
        item.status = valid ? "READY" : "FAILED";
        return item;
    }

    public void imported(UUID projectId) { this.projectId = projectId; this.status = "IMPORTED"; }
    public void skip() { this.status = "SKIPPED"; }
    public void fail(String errors) { this.errors = errors; this.status = "FAILED"; }
    public UUID id() { return id; }
    public int rowNumber() { return rowNumber; }
    public String status() { return status; }
    public String rowPayload() { return rowPayload; }
    public String errors() { return errors; }
    public UUID projectId() { return projectId; }
}
