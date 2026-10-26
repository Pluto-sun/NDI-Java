package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportBatchSummary(
        UUID batchId,
        String status,
        int totalRows,
        int validRows,
        int failedRows,
        int skippedRows,
        int importedRows,
        Instant confirmedAt,
        List<Row> rows) {
    public record Row(
            int rowNumber,
            String status,
            String errorsJson,
            UUID projectId,
            String sourceCellsJson,
            int revisionNo,
            com.pluto.ndi.projects.domain.ProjectImportNameReview nameReview) {}
}
