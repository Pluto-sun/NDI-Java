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

    public static ExcelWorkbook.Inspection inspect(String fileName, byte[] bytes, List<String> fields)
            throws IOException {
        return ExcelWorkbook.inspect(fileName, bytes, fields, ExcelWorkbook.INCLUDE_ALL_HEADERS, true);
    }

    public static List<ExcelWorkbook.Line> read(
            byte[] bytes,
            String sheetName,
            Integer rowNo,
            Map<String, String> mapping,
            List<String> fields)
            throws IOException {
        return ExcelWorkbook.read(bytes, sheetName, rowNo, mapping, fields, RevenueExcel::formatNumericCell, true);
    }

    private static String formatNumericCell(String field, Cell cell) {
        if (field.equals("审签日期") && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        if (field.equals("条款编号")) {
            var number = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
            if (number.signum() >= 0 && number.scale() <= 0) return number.toPlainString();
        }
        return switch (field) {
            case "条款金额", RevenueFields.SOURCE_AMOUNT, "认定金额" ->
                    BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            default -> null;
        };
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
            for (int i = 0; i < com.pluto.ndi.revenue.domain.RevenueImport.fields().size(); i++) {
                var field = com.pluto.ndi.revenue.domain.RevenueImport.fields().get(i);
                var cell = header.createCell(i);
                cell.setCellValue(field);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 26 * 256);
                sheet.setDefaultColumnStyle(i, text);
            }
            sheet.createFreezePane(0, 1);
            var dropdowns = new ExcelDropdowns(book, sheet, com.pluto.ndi.revenue.domain.RevenueImport.fields());
            var provinces =
                    new ArrayList<>(reference.provinces().stream().map(p -> p.name()).toList());
            provinces.add("具体组织");
            provinces.addAll(reference.fixedOrganizations().keySet());
            dropdowns.list("省份名称", provinces);
            reference
                    .fixedOrganizations()
                    .forEach(
                            (province, organization) ->
                                    dropdowns.conditionalList(
                                            "省份名称", province, "地市/组织名称", List.of(organization)));
            dropdowns.list(com.pluto.ndi.revenue.domain.RevenueImport.STATUS, com.pluto.ndi.revenue.domain.Revenue.PROJECT_STATUSES);
            var integratedOptions = new ArrayList<>(reference.provinces().stream().map(p -> p.name()).toList());
            integratedOptions.addAll(reference.integratedOrganizations());
            dropdowns.list("对应省分（集成）", integratedOptions);
            var notes = book.createSheet("填写说明");
            var lines =
                    List.of(
                            "来源金额单位：元，最多两位小数；不能把空值或--自动当作0。",
                            "必填：月账期、三化分类、条款金额、产品本月计收不含税额/标品含税收入。",
                            "月账期、费用账期支持YYYYMM或YYYY-MM；审签日期选填，支持Excel日期、横线或斜线日期、日期时间以及--（无日期）。",
                            "合同编号、条款编号选填；留空或--统一为空，仅纯数字条款编号去除前导零（002与2均保存为2），混合编号保留。",
                            "月账期＋合同编号＋条款编号＋业务号码/项目编号＋自然客户名称＋产品编码全局唯一；其中仅月账期必填，其余五项未填或--统一按空字符串参与判重。",
                            "省份名称选填，留空为未归属；选择具体组织时必须填写组织名称全称；标准省份下地市仍为文本。",
                            "省份名称选择数科时，组织名称仅允许联通数字科技有限公司本部；未填时系统自动补齐。",
                            "认定金额、通报项目名称、对应省分（集成）三项齐全且金额大于零时，导入为已认定，项目状态选填；其他情况忽略认定数据。",
                            "合同编号和业务号码等请用文本保存以保留前导零；纯数字条款编号按整数归一；单次最多1000行。");
            for (int i = 0; i < lines.size(); i++)
                notes.createRow(i).createCell(0).setCellValue(lines.get(i));
            notes.setColumnWidth(0, 110 * 256);
            book.write(out);
            return out.toByteArray();
        }
    }
}
