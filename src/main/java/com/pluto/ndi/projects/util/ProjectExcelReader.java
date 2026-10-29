package com.pluto.ndi.projects.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取严格模板或经人工确认映射的 Excel，不计算公式。 */
@Component
public class ProjectExcelReader {
    private static final String SHEET = "导入数据";
    private static final List<String> REQUIRED =
            List.of("省分", "地市", "客户名称", "客户所属行业", "项目名称", "项目建设内容", "项目阶段", "项目总金额（万）");
    private static final List<String> OPTIONAL =
            List.of(
                    "项目负责人",
                    "省分负责人",
                    "地市客户经理",
                    "签约主体",
                    "产品单位支撑专家",
                    "拟招标日期",
                    "应用场景",
                    "产品推广内容",
                    "备注",
                    "合同编号",
                    "签约金额（万）",
                    "重要度",
                    "支撑行业专家");
    private static final Pattern PROGRESS_DATE =
            Pattern.compile("项目进展（(\\d{4})年(\\d{1,2})月(\\d{1,2})日）");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static List<String> requiredHeaders() { return REQUIRED; }
    public static List<String> mappableHeaders() {
        var fields = new ArrayList<>(REQUIRED);
        fields.addAll(ProjectImportAmounts.fields());
        fields.addAll(OPTIONAL);
        return List.copyOf(fields);
    }

    public static Map<String, String> withoutLegacyCategory(Map<String, String> mapping) {
        var result = new LinkedHashMap<String, String>(mapping == null ? Map.of() : mapping);
        result.remove("三化类别");
        return java.util.Collections.unmodifiableMap(result);
    }

    public List<ExcelRow> read(byte[] data) throws IOException {
        return read(data, null, null, Map.of());
    }

    public List<ExcelRow> read(byte[] data, String sheetName, Integer headerRowNo,
            Map<String, String> fieldMapping) throws IOException {
        if (data.length == 0 || data.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Excel 文件大小必须在 1 字节至 10 MB 之间");
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            if (!workbook.getExternalLinksTable().isEmpty()) {
                throw new IllegalArgumentException("Excel 文件不得包含外部链接");
            }
            Sheet sheet = selectedSheet(workbook, sheetName);
            int headerIndex = headerRowNo == null ? findHeaderIndex(sheet, fieldMapping)
                    : headerRowNo - 1;
            if (headerIndex < 0 || headerIndex > 100 || sheet.getRow(headerIndex) == null) {
                throw new IllegalArgumentException("所选表头行不存在或超出前 101 行");
            }
            var formatter = new DataFormatter();
            var sourceHeaders = headers(sheet.getRow(headerIndex), formatter);
            // 兼容旧客户端和暂存映射；旧类别列不再读取，也不参与重复映射校验。
            fieldMapping = withoutLegacyCategory(fieldMapping);
            var mappedSources = fieldMapping.values().stream()
                    .filter(value -> value != null && !value.isBlank()).toList();
            if (fieldMapping.size() > 64 || !mappableHeaders().containsAll(fieldMapping.keySet())
                    || fieldMapping.values().stream().anyMatch(value -> value == null)
                    || new HashSet<>(mappedSources).size() != mappedSources.size()) {
                throw new IllegalArgumentException("字段映射目标或来源无效");
            }
            if (fieldMapping.isEmpty() && !sourceHeaders.keySet().containsAll(REQUIRED)) {
                throw new IllegalArgumentException("导入模板缺少必需列：" + REQUIRED.stream()
                        .filter(name -> !sourceHeaders.containsKey(name)).toList());
            }
            if (fieldMapping.isEmpty()
                    && ProjectImportAmounts.fields().stream()
                            .noneMatch(sourceHeaders::containsKey)) {
                throw new IllegalArgumentException("导入模板至少需要一项三化预计金额列");
            }
            var headers = new LinkedHashMap<String, Integer>();
            var usedColumns = new HashSet<Integer>();
            for (var required : mappableHeaders()) {
                var source = fieldMapping.getOrDefault(required, required);
                if (fieldMapping.containsKey(required) && !source.isBlank()
                        && !sourceHeaders.containsKey(source)) {
                    throw new IllegalArgumentException("映射来源列不存在：" + source);
                }
                int column = source.isBlank() ? -1 : sourceHeaders.getOrDefault(source, -1);
                if (column >= 0 && !usedColumns.add(column)) {
                    throw new IllegalArgumentException("同一 Excel 列不能映射到多个业务字段");
                }
                headers.put(required, column);
            }
            for (var source : sourceHeaders.entrySet()) {
                if (PROGRESS_DATE.matcher(source.getKey()).matches()) {
                    headers.put(source.getKey(), source.getValue());
                }
            }
            var result = new ArrayList<ExcelRow>();
            for (int r = headerIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                var cells = new HashMap<String, String>();
                for (var entry : headers.entrySet()) {
                    cells.put(
                            entry.getKey(),
                            entry.getValue() < 0
                                    ? ""
                                    : value(row.getCell(entry.getValue()), formatter));
                }
                if (cells.values().stream().allMatch(String::isBlank)) continue;
                if (result.size() == 1000) throw new IllegalArgumentException("单批次最多导入 1000 行");
                result.add(new ExcelRow(r + 1, Map.copyOf(cells)));
            }
            return result;
        }
    }

    public WorkbookInspection inspect(byte[] data, String fileName) throws IOException {
        if (data.length == 0 || data.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Excel 文件大小必须在 1 字节至 10 MB 之间");
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            if (!workbook.getExternalLinksTable().isEmpty()) {
                throw new IllegalArgumentException("Excel 文件不得包含外部链接");
            }
            var formatter = new DataFormatter();
            var sheets = new ArrayList<SheetInspection>();
            for (var sheet : workbook) {
                var candidates = new ArrayList<HeaderCandidate>();
                for (int index = 0; index <= Math.min(sheet.getLastRowNum(), 11); index++) {
                    var row = sheet.getRow(index);
                    if (row == null) continue;
                    // 候选行可能是业务数据或说明，不能把重复值、公式误判为模板错误。
                    var names = new ArrayList<>(candidateHeaders(row, formatter).keySet());
                    if (!names.isEmpty()) candidates.add(new HeaderCandidate(index + 1, names));
                }
                sheets.add(new SheetInspection(sheet.getSheetName(), candidates));
            }
            if (sheets.isEmpty()) throw new IllegalArgumentException("Excel 文件中没有工作表");
            return new WorkbookInspection(fileName, mappableHeaders(), sheets);
        }
    }

    private Sheet selectedSheet(XSSFWorkbook workbook, String requested) {
        Sheet sheet = requested == null || requested.isBlank()
                ? workbook.getSheet(SHEET) : workbook.getSheet(requested);
        if (sheet == null && (requested == null || requested.isBlank()) && workbook.getNumberOfSheets() > 0) {
            sheet = workbook.getSheetAt(0);
        }
        if (sheet == null) throw new IllegalArgumentException("所选工作表不存在");
        return sheet;
    }

    private int findHeaderIndex(Sheet sheet, Map<String, String> mapping) {
        if (mapping != null && !mapping.isEmpty()) {
            throw new IllegalArgumentException("手工映射必须指定表头行");
        }
        var formatter = new DataFormatter();
        for (int index = 0; index <= Math.min(sheet.getLastRowNum(), 7); index++) {
            var row = sheet.getRow(index);
            if (row == null) continue;
            Set<String> names = candidateHeaders(row, formatter).keySet();
            if (names.contains("项目名称") && names.contains("省分")) return index;
        }
        throw new IllegalArgumentException("未识别到项目表头，请选择表头行并设置字段映射");
    }

    private Map<String, Integer> headers(Row row, DataFormatter formatter) {
        var result = new LinkedHashMap<String, Integer>();
        if (row.getLastCellNum() > 128) throw new IllegalArgumentException("表头列数不得超过 128");
        for (int column = 0; column < row.getLastCellNum(); column++) {
            var name = value(row.getCell(column), formatter);
            if (!name.isBlank() && result.putIfAbsent(name, column) != null) {
                throw new IllegalArgumentException("模板存在重复表头：" + name);
            }
        }
        return result;
    }

    private Map<String, Integer> candidateHeaders(Row row, DataFormatter formatter) {
        var result = new LinkedHashMap<String, Integer>();
        if (row.getLastCellNum() > 128) return result;
        for (int column = 0; column < row.getLastCellNum(); column++) {
            var cell = row.getCell(column);
            if (cell == null || cell.getCellType() == CellType.FORMULA) continue;
            var name = value(cell, formatter);
            if (!name.isBlank()) result.putIfAbsent(name, column);
        }
        return result;
    }

    private String value(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException("导入模板不得包含公式，位置：" + cell.getAddress());
        }
        return formatter.formatCellValue(cell).strip();
    }

    public static List<ProgressColumn> progressColumns(Map<String, String> cells) {
        var result = new ArrayList<ProgressColumn>();
        for (var entry : cells.entrySet()) {
            Matcher matcher = PROGRESS_DATE.matcher(entry.getKey());
            if (!entry.getValue().isBlank() && matcher.matches()) {
                var date = LocalDate.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                result.add(new ProgressColumn(date.atStartOfDay(ZONE).toInstant(), entry.getValue()));
            }
        }
        result.sort(java.util.Comparator.comparing(ProgressColumn::occurredAt));
        return result;
    }

    public record ExcelRow(int number, Map<String, String> cells) {
        public String get(String header) { return cells.getOrDefault(header, ""); }
    }
    public record ProgressColumn(java.time.Instant occurredAt, String content) { }
    public record WorkbookInspection(String fileName, List<String> fields, List<SheetInspection> sheets) { }
    public record SheetInspection(String name, List<HeaderCandidate> headerCandidates) { }
    public record HeaderCandidate(int rowNo, List<String> headers) { }
}
