package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_import_batch")
public class ProjectImportBatchEntity {
    @Id private UUID id;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "file_sha256", nullable = false, length = 64) private String fileSha256;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "valid_rows", nullable = false) private int validRows;
    @Column(name = "failed_rows", nullable = false) private int failedRows;
    @Column(name = "imported_rows", nullable = false) private int importedRows;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;

    protected ProjectImportBatchEntity() { }

    public static ProjectImportBatchEntity preview(UUID actorId, String fileName, String sha256,
            int totalRows, int validRows, Instant at) {
        var item = new ProjectImportBatchEntity();
        item.id = UUID.randomUUID();
        item.actorId = actorId;
        item.fileName = fileName;
        item.fileSha256 = sha256;
        item.totalRows = totalRows;
        item.validRows = validRows;
        item.failedRows = totalRows - validRows;
        item.status = "PREVIEWED";
        item.createdAt = at;
        return item;
    }

    public void beginConfirmation(Instant at) {
        if (!"PREVIEWED".equals(status)) throw new IllegalStateException("导入批次已经确认");
        status = "CONFIRMING";
        confirmedAt = at;
    }

    public void finish(int importedRows) {
        this.importedRows = importedRows;
        this.failedRows = totalRows - importedRows;
        status = "COMPLETED";
    }

    public UUID id() { return id; }
    public UUID actorId() { return actorId; }
    public String status() { return status; }
    public int totalRows() { return totalRows; }
    public int validRows() { return validRows; }
    public int failedRows() { return failedRows; }
    public int importedRows() { return importedRows; }
}
