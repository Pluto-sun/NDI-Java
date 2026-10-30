package com.pluto.ndi.shared.imports;

import com.pluto.ndi.shared.excel.ExcelWorkbook;

import java.time.Instant;
import java.util.*;

/** 三步导入的公共数据协议，业务字段与查重策略由模块提供。 */
public class ImportModels {
    protected ImportModels() {}

    public record Rule(String name, String requirement, String hint) {}

    public record Configuration(
            String sheetName, Integer headerRowNo, Map<String, String> fieldMapping) {}

    public record Row(
            int rowNumber,
            String status,
            String errorsJson,
            UUID recordId,
            String sourceCellsJson,
            long revisionNo,
            Object nameReview) {}

    public record Batch(
            UUID batchId,
            String status,
            int totalRows,
            int validRows,
            int failedRows,
            int skippedRows,
            int importedRows,
            Instant confirmedAt,
            List<Row> rows) {}

    public record Task(
            UUID id,
            long version,
            int step,
            String fileName,
            ExcelWorkbook.Inspection inspection,
            Configuration configuration,
            List<Integer> selectedRows,
            Batch batch,
            Instant updatedAt,
            List<Rule> fieldRules) {}
}
