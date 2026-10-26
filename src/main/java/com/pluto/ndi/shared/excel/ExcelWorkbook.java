package com.pluto.ndi.shared.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

/** 工作簿基础读写不包含业务字段规则，不执行公式及外部链接。 */
public final class ExcelWorkbook {
    private ExcelWorkbook() {}

    public record Header(int rowNo, List<String> headers) {}

    public record SheetInfo(String name, List<Header> headerCandidates) {}

    public record Inspection(String fileName, List<String> fields, List<SheetInfo> sheets) {}

    public record Line(int number, Map<String, String> cells) {}

    private static XSSFWorkbook open(byte[] data) throws IOException {
        if (data.length == 0 || data.length > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Excel 文件须为 1 字节至 10 MB");
        var workbook = new XSSFWorkbook(new ByteArrayInputStream(data));
        if (!workbook.getExternalLinksTable().isEmpty()) {
            workbook.close();
            throw new IllegalArgumentException("Excel 不得包含外部链接");
        }
        return workbook;
    }

    public static Inspection inspect(String fileName, byte[] data, List<String> fields)
            throws IOException {
        try (var wb = open(data)) {
            var sheets = new ArrayList<SheetInfo>();
            if (wb.getNumberOfSheets() > 50) throw new IllegalArgumentException("工作表不能超过 50 个");
            for (var sheet : wb) {
                var headers = new ArrayList<Header>();
                for (int i = 0; i <= Math.min(100, sheet.getLastRowNum()); i++) {
                    var row = sheet.getRow(i);
                    if (row == null || row.getLastCellNum() > 128) continue;
                    try {
                        var names = headers(row);
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
        try (var wb = open(data)) {
            var sheet = wb.getSheet(sheetName);
            if (sheet == null || rowNo == null || rowNo < 1 || rowNo > 101)
                throw new IllegalArgumentException("请选择有效工作表和表头");
            var row = sheet.getRow(rowNo - 1);
            if (row == null) throw new IllegalArgumentException("所选表头不存在");
            var source = headers(row);
            if (!fields.containsAll(mapping.keySet())) throw new IllegalArgumentException("映射字段无效");
            var used = new HashSet<String>();
            for (var value : mapping.values())
                if (value == null
                        || !value.isBlank() && (!source.containsKey(value) || !used.add(value)))
                    throw new IllegalArgumentException("映射列不存在或重复使用");
            var result = new ArrayList<Line>();
            for (int i = rowNo; i <= sheet.getLastRowNum(); i++) {
                var dataRow = sheet.getRow(i);
                if (dataRow == null) continue;
                var cells = new LinkedHashMap<String, String>();
                for (var field : fields) {
                    var column = source.get(mapping.getOrDefault(field, ""));
                    cells.put(field, column == null ? "" : value(dataRow.getCell(column)));
                }
                if (cells.values().stream().allMatch(String::isBlank)) continue;
                result.add(new Line(i + 1, cells));
                if (result.size() > 1000) throw new IllegalArgumentException("一次最多导入 1000 行");
            }
            if (result.isEmpty()) throw new IllegalArgumentException("所选工作表没有可导入内容");
            return result;
        }
    }

    public static byte[] template(List<String> fields) throws IOException {
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("导入数据");
            var row = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                row.createCell(i).setCellValue(fields.get(i));
                sheet.setColumnWidth(i, 24 * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static LinkedHashMap<String, Integer> headers(Row row) {
        if (row.getLastCellNum() > 128) throw new IllegalArgumentException("表头最多 128 列");
        var names = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            var name = value(row.getCell(i));
            if (!name.isBlank() && names.putIfAbsent(name, i) != null)
                throw new IllegalArgumentException("重复表头：" + name);
        }
        return names;
    }

    private static String value(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA)
            throw new IllegalArgumentException("不支持公式，请先粘贴为值：" + cell.getAddress());
        return new DataFormatter(Locale.CHINA).formatCellValue(cell).strip();
    }
}
