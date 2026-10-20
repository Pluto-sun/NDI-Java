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
    @Column(name = "source_payload", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String sourcePayload;
    @Column(name = "revision_no", nullable = false) private int revisionNo;
    @Column(nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String errors;
    @Column(name = "project_id") private UUID projectId;

    protected ProjectImportRowEntity() { }

    public static ProjectImportRowEntity preview(UUID batchId, int number, String key,
            String payload, String sourcePayload, String errors, boolean valid) {
        var item = new ProjectImportRowEntity();
        item.id = UUID.randomUUID();
        item.batchId = batchId;
        item.rowNumber = number;
        item.normalizedKey = key;
        item.rowPayload = payload;
        item.sourcePayload = sourcePayload;
        item.errors = errors;
        item.status = valid ? "READY" : "FAILED";
        return item;
    }

    public void imported(UUID projectId) { this.projectId = projectId; this.status = "IMPORTED"; }
    public void skip() { this.status = "SKIPPED"; }
    public void fail(String errors) { this.errors = errors; this.status = "FAILED"; }
    public void revise(String sourcePayload, String payload, String key, String errors, boolean valid) {
        if (!"READY".equals(status) && !"FAILED".equals(status)) {
            throw new IllegalStateException("仅预检行可以修正");
        }
        this.sourcePayload = sourcePayload;
        this.rowPayload = payload;
        this.normalizedKey = key;
        this.errors = errors;
        this.status = valid ? "READY" : "FAILED";
        this.revisionNo++;
    }
    public UUID id() { return id; }
    public int rowNumber() { return rowNumber; }
    public String status() { return status; }
    public String rowPayload() { return rowPayload; }
    public String sourcePayload() { return sourcePayload; }
    public String normalizedKey() { return normalizedKey; }
    public int revisionNo() { return revisionNo; }
    public String errors() { return errors; }
    public UUID projectId() { return projectId; }
}
