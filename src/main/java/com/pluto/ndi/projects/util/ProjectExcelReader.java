package com.pluto.ndi.projects.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** 只接受已核对的项目导入表头，不读取公式计算结果。 */
@Component
public class ProjectExcelReader {
    private static final String SHEET = "导入数据";
    private static final List<String> REQUIRED = List.of("省分", "地市", "客户名称", "客户所属行业",
            "项目名称", "省分负责人", "地市客户经理", "项目建设内容", "三化类别",
            "项目阶段", "项目总金额（万）", "网络化预计金额（万）", "格物预计金额（万）",
            "大数据预计金额（万）", "万悟预计金额（万）");
    private static final Pattern PROGRESS_DATE = Pattern.compile("项目进展（(\\d{4})年(\\d{1,2})月(\\d{1,2})日）");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static List<String> requiredHeaders() { return REQUIRED; }

    public List<ExcelRow> read(byte[] data) throws IOException {
        if (data.length == 0 || data.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Excel 文件大小必须在 1 字节至 10 MB 之间");
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            if (!workbook.getExternalLinksTable().isEmpty()) {
                throw new IllegalArgumentException("Excel 文件不得包含外部链接");
            }
            Sheet sheet = workbook.getSheet(SHEET);
            if (sheet == null || sheet.getRow(0) == null) {
                throw new IllegalArgumentException("缺少导入数据工作表或表头");
            }
            if (sheet.getLastRowNum() > 1000) {
                throw new IllegalArgumentException("单批次最多导入 1000 行");
            }
            var formatter = new DataFormatter();
            var headers = new HashMap<String, Integer>();
            var headerRow = sheet.getRow(0);
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                var value = value(headerRow.getCell(c), formatter);
                if (!value.isBlank() && headers.putIfAbsent(value, c) != null) {
                    throw new IllegalArgumentException("模板存在重复表头：" + value);
                }
            }
            if (!headers.keySet().containsAll(REQUIRED)) {
                throw new IllegalArgumentException("导入模板缺少必需列：" + REQUIRED.stream()
                        .filter(name -> !headers.containsKey(name)).toList());
            }
            var result = new ArrayList<ExcelRow>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                var cells = new HashMap<String, String>();
                for (var entry : headers.entrySet()) {
                    cells.put(entry.getKey(), value(row.getCell(entry.getValue()), formatter));
                }
                if (cells.values().stream().allMatch(String::isBlank)) continue;
                result.add(new ExcelRow(r + 1, Map.copyOf(cells)));
            }
            return result;
        }
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
}
