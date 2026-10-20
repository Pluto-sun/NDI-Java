package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectImportBatchEntity;

/** 导入计划列表不携带逐行内容。 */
public record ImportBatchListItem(UUID batchId, String fileName, String status,
        int totalRows, int validRows, int failedRows, int importedRows,
        Instant createdAt, Instant confirmedAt) {
    public static ImportBatchListItem from(ProjectImportBatchEntity batch) {
        return new ImportBatchListItem(batch.id(), batch.fileName(), batch.status(),
                batch.totalRows(), batch.validRows(), batch.failedRows(), batch.importedRows(),
                batch.createdAt(), batch.confirmedAt());
    }
}
