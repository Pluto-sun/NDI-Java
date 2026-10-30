package com.pluto.ndi.revenue.util;

import com.pluto.ndi.revenue.service.RevenueService.Reference;
import com.pluto.ndi.shared.excel.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** 日期读取为日历日期，金额读取原始数值；文本标识沿用单元格格式保留前导零。 */
public final class RevenueExcel {
    private RevenueExcel() {}

    public static List<ExcelWorkbook.Line> read(
            byte[] bytes,
            String sheetName,
            Integer rowNo,
            Map<String, String> mapping,
            List<String> fields)
            throws IOException {
        // 共用读取器先验证文件、列映射、公式和行数，计收再处理日期及金额原值。
        var lines = ExcelWorkbook.read(bytes, sheetName, rowNo, mapping, fields);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheet(sheetName);
            var header = sheet.getRow(rowNo - 1);
            var columns = new HashMap<String, Integer>();
            var format = new DataFormatter(Locale.CHINA);
            for (var cell : header)
                columns.put(format.formatCellValue(cell).strip(), cell.getColumnIndex());
            var result = new ArrayList<ExcelWorkbook.Line>();
            for (var line : lines) {
                var cells = new LinkedHashMap<>(line.cells());
                var row = sheet.getRow(line.number() - 1);
                for (var field : fields) {
                    var column = columns.get(mapping.getOrDefault(field, ""));
                    if (column == null) continue;
                    var cell = row.getCell(column);
                    if (cell == null || cell.getCellType() != CellType.NUMERIC) continue;
                    if (field.equals("审签日期") && DateUtil.isCellDateFormatted(cell))
                        cells.put(field, cell.getLocalDateTimeCellValue().toLocalDate().toString());
                    else if (Set.of("条款金额", RevenueFields.SOURCE_AMOUNT).contains(field))
                        cells.put(
                                field,
                                BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString());
                }
                if (cells.values().stream().allMatch(String::isBlank)) continue;
                result.add(new ExcelWorkbook.Line(line.number(), cells));
            }
            return result;
        }
    }

    public static byte[] template(Reference reference) throws IOException {
        try (var book = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("导入数据");
            var header = sheet.createRow(0);
            var style = book.createCellStyle();
            var font = book.createFont();
            font.setBold(true);
            font.setFontName("Arial");
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var text = book.createCellStyle();
            text.setDataFormat(book.createDataFormat().getFormat("@"));
            for (int i = 0; i < RevenueFields.FIELDS.size(); i++) {
                var field = RevenueFields.FIELDS.get(i);
                var cell = header.createCell(i);
                cell.setCellValue(field);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 26 * 256);
                sheet.setDefaultColumnStyle(i, text);
            }
            sheet.createFreezePane(0, 1);
            var dropdowns = new ExcelDropdowns(book, sheet, RevenueFields.FIELDS);
            var provinces =
                    new ArrayList<>(reference.provinces().stream().map(p -> p.name()).toList());
            provinces.add("具体组织");
            dropdowns.list("省份名称", provinces);
            var notes = book.createSheet("填写说明");
            var lines =
                    List.of(
                            "来源金额单位：元，最多两位小数；不能把空值或--自动当作0。",
                            "必填：月账期、省份名称、合同编号、条款编号、条款金额、产品本月计收不含税额/标品含税收入。",
                            "月账期、费用账期支持YYYYMM或YYYY-MM；审签日期支持Excel日期或YYYY-MM-DD。",
                            "月账期＋合同编号＋条款编号全局唯一，同月重复行须修正，不自动覆盖或丢弃。",
                            "具体组织必须填写地市/组织名称全称；标准省份下地市仍为文本。",
                            "仅导入33项来源字段，不导入认定金额、通报项目名称、项目状态及对应省分（集成）。",
                            "业务标识请用文本保存以保留前导零；单次最多1000行。");
            for (int i = 0; i < lines.size(); i++)
                notes.createRow(i).createCell(0).setCellValue(lines.get(i));
            notes.setColumnWidth(0, 110 * 256);
            book.write(out);
            return out.toByteArray();
        }
    }
}
