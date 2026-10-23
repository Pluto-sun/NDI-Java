package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_import_batch")
public class ProjectImportBatchEntity {
    @Id private UUID id;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "file_sha256", nullable = false, length = 64) private String fileSha256;
    @Column(name = "sheet_name", length = 31) private String sheetName;
    @Column(name = "header_row_number") private Integer headerRowNumber;
    @Column(name = "field_mapping", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String fieldMapping;
    @Column(nullable = false, length = 24) private String status;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "valid_rows", nullable = false) private int validRows;
    @Column(name = "failed_rows", nullable = false) private int failedRows;
    @Column(name = "imported_rows", nullable = false) private int importedRows;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;

    protected ProjectImportBatchEntity() { }

    public static ProjectImportBatchEntity preview(UUID actorId, String fileName, String sha256,
            int totalRows, int validRows, Instant at, String sheetName, Integer headerRowNumber,
            String fieldMapping) {
        var item = new ProjectImportBatchEntity();
        item.id = UUID.randomUUID();
        item.actorId = actorId;
        item.fileName = fileName;
        item.fileSha256 = sha256;
        item.sheetName = sheetName;
        item.headerRowNumber = headerRowNumber;
        item.fieldMapping = fieldMapping;
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

    public void finish(int importedRows, int skippedRows) {
        if (!"CONFIRMING".equals(status)) throw new IllegalStateException("导入批次不在确认状态");
        this.importedRows = importedRows;
        this.failedRows = totalRows - importedRows - skippedRows;
        status = "COMPLETED";
    }

    public void cancel() {
        if (!"PREVIEWED".equals(status)) throw new IllegalStateException("仅预检中的导入计划可取消");
        status = "CANCELLED";
    }

    /** 零入库计划重新映射后停用，原预检行继续保留供核对。 */
    public void supersede() {
        if (importedRows != 0 || "CONFIRMING".equals(status)) {
            throw new IllegalStateException("已有入库记录或正在导入的计划不能重新映射");
        }
        status = "CANCELLED";
    }

    public void refreshPreviewCounts(int validRows) {
        if (!"PREVIEWED".equals(status)) throw new IllegalStateException("导入计划不在预检状态");
        this.validRows = validRows;
        this.failedRows = totalRows - validRows;
    }

    public UUID id() { return id; }
    public UUID actorId() { return actorId; }
    public String fileName() { return fileName; }
    public String fileSha256() { return fileSha256; }
    public String sheetName() { return sheetName; }
    public Integer headerRowNumber() { return headerRowNumber; }
    public String fieldMapping() { return fieldMapping; }
    public String status() { return status; }
    public int totalRows() { return totalRows; }
    public int validRows() { return validRows; }
    public int failedRows() { return failedRows; }
    public int importedRows() { return importedRows; }
    public Instant createdAt() { return createdAt; }
    public Instant confirmedAt() { return confirmedAt; }
}
