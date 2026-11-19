package com.pluto.ndi.shared.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 工作簿基础读写不包含业务字段规则，不执行公式及外部链接。 */
public final class ExcelWorkbook {
    private ExcelWorkbook() {}

    public static final Predicate<String> INCLUDE_ALL_HEADERS = name -> true;

    public record Header(int rowNo, List<String> headers) {}

    public record SheetInfo(String name, List<Header> headerCandidates) {}

    public record Inspection(String fileName, List<String> fields, List<SheetInfo> sheets) {}

    public record Line(int number, Map<String, String> cells) {}

    private static XSSFWorkbook open(byte[] data) throws IOException {
        if (data.length == 0 || data.length > ExcelImportLimits.MAX_BYTES)
            throw new IllegalArgumentException("Excel 文件须为 1 字节至 20 MB");
        var workbook = new XSSFWorkbook(new ByteArrayInputStream(data));
        if (!workbook.getExternalLinksTable().isEmpty()) {
            workbook.close();
            throw new IllegalArgumentException("Excel 不得包含外部链接");
        }
        return workbook;
    }

    public static Inspection inspect(String fileName, byte[] data, List<String> fields)
            throws IOException {
        return inspect(fileName, data, fields, INCLUDE_ALL_HEADERS);
    }

    public static Inspection inspect(
            String fileName, byte[] data, List<String> fields, Predicate<String> includeHeader)
            throws IOException {
        return inspect(fileName, data, fields, includeHeader, false);
    }

    public static Inspection inspect(String fileName, byte[] data, List<String> fields,
            Predicate<String> includeHeader, boolean distinguishDuplicateHeaders) throws IOException {
        try (var wb = open(data)) {
            var sheets = new ArrayList<SheetInfo>();
            if (wb.getNumberOfSheets() > 50) throw new IllegalArgumentException("工作表不能超过 50 个");
            for (var sheet : wb) {
                int index = wb.getSheetIndex(sheet);
                if (wb.isSheetHidden(index) || wb.isSheetVeryHidden(index)) continue;
                var headers = new ArrayList<Header>();
                for (int i = 0; i <= Math.min(100, sheet.getLastRowNum()); i++) {
                    var row = sheet.getRow(i);
                    if (row == null || row.getLastCellNum() > 128) continue;
                    try {
                        var names = headers(row, includeHeader, distinguishDuplicateHeaders);
                        if (!names.isEmpty())
                            headers.add(new Header(i + 1, List.copyOf(names.keySet())));
                    } catch (IllegalArgumentException ignored) {
                        /* 非表头行允许含公式，选定表头及数据行会严格校验。 */
                    }
                }
                sheets.add(new SheetInfo(sheet.getSheetName(), headers));
            }
            return new Inspection(fileName, fields, sheets);
        }
    }

    public static List<Line> read(
            byte[] data,
            String sheetName,
            Integer rowNo,
            Map<String, String> mapping,
            List<String> fields)
            throws IOException {
        return read(data, sheetName, rowNo, mapping, fields, Set.of());
    }

    public static List<Line> read(byte[] data, String sheetName, Integer rowNo,
            Map<String, String> mapping, List<String> fields, Set<String> dateFields) throws IOException {
        return read(data, sheetName, rowNo, mapping, fields, dateFields, INCLUDE_ALL_HEADERS);
    }

    public static List<Line> read(byte[] data, String sheetName, Integer rowNo,
            Map<String, String> mapping, List<String> fields, Set<String> dateFields,
            Predicate<String> includeHeader) throws IOException {
        return read(data, sheetName, rowNo, mapping, fields, dateFields, includeHeader, Set.of());
    }

    public static List<Line> read(byte[] data, String sheetName, Integer rowNo,
            Map<String, String> mapping, List<String> fields, Set<String> dateFields,
            Predicate<String> includeHeader, Set<String> decimalFields) throws IOException {
        return read(data, sheetName, rowNo, mapping, fields, dateFields, includeHeader,
                decimalFields, (field, cell) -> null, false);
    }

    public static List<Line> read(byte[] data, String sheetName, Integer rowNo,
            Map<String, String> mapping, List<String> fields,
            java.util.function.BiFunction<String, Cell, String> formatNumericCell,
            boolean distinguishDuplicateHeaders) throws IOException {
        return read(data, sheetName, rowNo, mapping, fields, Set.of(), INCLUDE_ALL_HEADERS,
                Set.of(), formatNumericCell, distinguishDuplicateHeaders);
    }

    private static List<Line> read(byte[] data, String sheetName, Integer rowNo,
            Map<String, String> mapping, List<String> fields, Set<String> dateFields,
            Predicate<String> includeHeader, Set<String> decimalFields,
            java.util.function.BiFunction<String, Cell, String> formatNumericCell,
            boolean distinguishDuplicateHeaders) throws IOException {
        try (var wb = open(data)) {
            var sheet = wb.getSheet(sheetName);
            if (sheet == null || rowNo == null || rowNo < 1 || rowNo > 101)
                throw new IllegalArgumentException("请选择有效工作表和表头");
            var row = sheet.getRow(rowNo - 1);
            if (row == null) throw new IllegalArgumentException("所选表头不存在");
            var source = headers(row, includeHeader, distinguishDuplicateHeaders);
            if (!fields.containsAll(mapping.keySet())) throw new IllegalArgumentException("映射字段无效");
            var used = new HashSet<String>();
            for (var value : mapping.values())
                if (value == null
                        || !value.isBlank() && (!source.containsKey(value) || !used.add(value)))
                    throw new IllegalArgumentException("映射列不存在或重复使用");
            var result = new ArrayList<Line>();
            var formatter = new DataFormatter(Locale.CHINA);
            int dataRows = 0;
            for (int i = rowNo; i <= sheet.getLastRowNum(); i++) {
                var dataRow = sheet.getRow(i);
                if (!hasData(dataRow)) continue;
                ExcelImportLimits.checkRowCount(++dataRows);
                var cells = new LinkedHashMap<String, String>();
                for (var field : fields) {
                    var column = source.get(mapping.getOrDefault(field, ""));
                    var cell = column == null ? null : dataRow.getCell(column);
                    if ((dateFields.contains(field) || decimalFields.contains(field))
                            && cell instanceof XSSFCell numeric && cell.getCellType() == CellType.NUMERIC) {
                        boolean date = DateUtil.isCellDateFormatted(cell);
                        if (date && dateFields.contains(field)) {
                            cells.put(field, cell.getLocalDateTimeCellValue().toLocalDate().toString());
                            continue;
                        }
                        if (!date && decimalFields.contains(field)) {
                            // 金额校验读取存储值，避免单元格显示格式先行舍入。
                            var raw = numeric.getRawValue();
                            cells.put(field, raw == null ? "" : new BigDecimal(raw).stripTrailingZeros().toString());
                            continue;
                        }
                    }
                    var formatted = cell != null && cell.getCellType() == CellType.NUMERIC
                            ? formatNumericCell.apply(field, cell) : null;
                    cells.put(field, formatted != null ? formatted : value(cell, formatter));
                }
                if (cells.values().stream().allMatch(String::isBlank)) continue;
                result.add(new Line(i + 1, cells));
            }
            if (result.isEmpty()) throw new IllegalArgumentException("所选工作表没有可导入内容");
            return result;
        }
    }

    public static boolean hasData(Row row) {
        if (row == null) return false;
        for (var cell : row) {
            switch (cell.getCellType()) {
                case BLANK, _NONE -> { }
                case STRING -> {
                    if (!cell.getStringCellValue().isBlank()) return true;
                }
                default -> { return true; }
            }
        }
        return false;
    }

    public static byte[] template(List<String> fields) throws IOException {
        return template(fields, Set.of());
    }

    public static byte[] template(List<String> fields, Set<String> textFields) throws IOException {
        return template(fields, textFields, null);
    }

    public static byte[] template(
            List<String> fields, Set<String> textFields, Consumer<ExcelDropdowns> configureDropdowns)
            throws IOException {
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("导入数据");
            var row = sheet.createRow(0);
            var textStyle = wb.createCellStyle();
            textStyle.setDataFormat(wb.createDataFormat().getFormat("@"));
            for (int i = 0; i < fields.size(); i++) {
                if (textFields.contains(fields.get(i))) sheet.setDefaultColumnStyle(i, textStyle);
                row.createCell(i).setCellValue(fields.get(i));
                sheet.setColumnWidth(i, 24 * 256);
            }
            sheet.createFreezePane(0, 1);
            if (configureDropdowns != null)
                configureDropdowns.accept(new ExcelDropdowns(wb, sheet, fields));
            var guide = wb.createSheet("填写说明");
            guide.createRow(0).createCell(0).setCellValue("每次最多导入 2000 行数据，表头不计入。");
            guide.createRow(1).createCell(0).setCellValue("Excel 文件最大 20 MB。请填写“导入数据”工作表。");
            guide.setColumnWidth(0, 58 * 256);
            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] originalRows(
            byte[] source, String sheetName, int headerRowNumber, List<Integer> rowNumbers)
            throws IOException {
        try (var original = new XSSFWorkbook(new ByteArrayInputStream(source));
                var result = new XSSFWorkbook()) {
            var input = original.getSheet(sheetName);
            var output = result.createSheet(sheetName);
            result.getCTWorkbook().getWorkbookPr().setDate1904(original.isDate1904());
            var formats = new HashMap<String, org.apache.poi.ss.usermodel.CellStyle>();
            var dataFormat = result.createDataFormat();
            var sourceRows = new ArrayList<Integer>();
            sourceRows.add(headerRowNumber);
            sourceRows.addAll(rowNumbers);
            for (int index = 0; index < sourceRows.size(); index++) {
                Row sourceRow = input.getRow(sourceRows.get(index) - 1);
                var targetRow = output.createRow(index);
                for (var cell : sourceRow) {
                    var target = targetRow.createCell(cell.getColumnIndex());
                    // 未映射列可能含公式；只复制其缓存值，避免行号变化后计算出不同结果。
                    var type = cell.getCellType() == CellType.FORMULA
                            ? cell.getCachedFormulaResultType() : cell.getCellType();
                    switch (type) {
                        case STRING -> target.setCellValue(cell.getStringCellValue());
                        case NUMERIC -> {
                            target.setCellValue(cell.getNumericCellValue());
                            var format = cell.getCellStyle().getDataFormatString();
                            if (format != null && !"General".equals(format)) {
                                target.setCellStyle(formats.computeIfAbsent(format, value -> {
                                    var style = result.createCellStyle();
                                    style.setDataFormat(dataFormat.getFormat(value));
                                    return style;
                                }));
                            }
                        }
                        case BOOLEAN -> target.setCellValue(cell.getBooleanCellValue());
                        case ERROR -> target.setCellErrorValue(cell.getErrorCellValue());
                        default -> target.setBlank();
                    }
                }
            }
            output.createFreezePane(0, 1);
            try (var out = new ByteArrayOutputStream()) {
                result.write(out);
                return out.toByteArray();
            }
        }
    }

    private static LinkedHashMap<String, Integer> headers(Row row, Predicate<String> includeHeader,
            boolean distinguishDuplicateHeaders) {
        if (row.getLastCellNum() > 128) throw new IllegalArgumentException("表头最多 128 列");
        var titles = new LinkedHashMap<Integer, String>();
        var counts = new HashMap<String, Integer>();
        var formatter = new DataFormatter(Locale.CHINA);
        for (int i = 0; i < row.getLastCellNum(); i++) {
            var name = value(row.getCell(i), formatter);
            if (name.isBlank() || !includeHeader.test(name)) continue;
            if (counts.merge(name, 1, Integer::sum) > 1 && !distinguishDuplicateHeaders)
                throw new IllegalArgumentException("重复表头：" + name);
            titles.put(i, name);
        }

        var names = new LinkedHashMap<String, Integer>();
        var reserved = new HashSet<>(counts.keySet());
        titles.forEach((column, name) -> {
            if (counts.get(name) > 1) {
                var suffix = "（" + CellReference.convertNumToColString(column) + "列）";
                do {
                    name += suffix;
                } while (!reserved.add(name));
            }
            names.put(name, column);
        });
        return names;
    }

    private static String value(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA)
            throw new IllegalArgumentException("不支持公式，请先粘贴为值：" + cell.getAddress());
        return formatter.formatCellValue(cell).strip();
    }
}
