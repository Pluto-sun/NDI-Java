package com.pluto.ndi.production.domain;

import com.pluto.ndi.shared.excel.ExcelWorkbook;

import java.time.Instant;
import java.util.*;

/** 未完成任务是唯一可恢复状态，确认后不保存可续导批次。 */
public final class ProductionImport {
    private ProductionImport() {}

    public static final List<String> REQUIRED =
            List.of("接口人", "排产月份", "省分", "地市", "项目名称", "客户名称", "三化类别");
    public static final List<String> AMOUNTS =
            List.of("工业专网排产（万元）", "格物平台排产（万元）", "工业大数据排产（万元）", "万悟平台排产（万元）");

    public static List<String> fields() {
        var fields = new ArrayList<>(REQUIRED);
        fields.addAll(AMOUNTS);
        fields.add("项目状态");
        return List.copyOf(fields);
    }

    public record Rule(String name, String requirement, String hint) {}

    public static List<Rule> rules() {
        return fields().stream()
                .map(
                        f ->
                                new Rule(
                                        f,
                                        REQUIRED.contains(f)
                                                ? "REQUIRED"
                                                : AMOUNTS.contains(f) ? "CONDITIONAL" : "OPTIONAL",
                                        AMOUNTS.contains(f)
                                                ? "四项至少填写一项，万元、非负、最多两位小数；金额须对应已选类别"
                                                : "三化类别".equals(f)
                                                        ? "网络化、格物、大数据、万悟，多项以分号分隔"
                                                        : "排产月份".equals(f) ? "yyyy-MM" : ""))
                .toList();
    }

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
