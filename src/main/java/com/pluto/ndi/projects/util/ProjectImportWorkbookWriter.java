package com.pluto.ndi.projects.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 生成与预检表头同源的空模板和批次失败行核对表。 */
@Component
public class ProjectImportWorkbookWriter {
    private final ObjectMapper json;

    public ProjectImportWorkbookWriter(ObjectMapper json) {
        this.json = json;
    }

    public byte[] template() {
        var headers = new ArrayList<>(ProjectExcelReader.mappableHeaders());
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("导入数据");
            writeHeader(book, sheet, headers);
            var instructions = book.createSheet("填写说明");
            var font = book.createFont();
            font.setFontName("Arial");
            var style = book.createCellStyle();
            style.setFont(font);
            List<String> notes =
                    List.of(
                            "请勿修改“导入数据”工作表名称和表头；单次最多 1000 条，文件不超过 10 MB。",
                            "必填：省分、地市、客户名称、客户所属行业、项目名称、项目负责人、客户需求、项目阶段、项目总金额。",
                            "三化类别根据四类预计金额自动识别：有值（含0）代表包含该类别，空值代表不包含；至少填写一项，合计不得超过项目总金额，单位为万元。",
                            "项目阶段支持方案交流、招投标、签约、计收和已终止；计收阶段必须填写合同编号和签约金额（万元）。",
                            "合同编号全局唯一，最长100个字符；签约或已终止项目可保留合同信息，填写时编号和金额须同时提供；方案交流和招投标阶段不得填写。",
                            "首次项目进展可写入以“项目进展（YYYY年M月D日）”命名的列；可按相同格式增加其他日期列。",
                            "项目负责人至少一人，多人以中英文分号分隔，导入后在项目详情配置职责并按需绑定账号。",
                            "负责人不必为系统账号，职责和账号绑定不授予项目权限；重复项目仅保留同地市同名的第一条。",
                            "拟招标月份选填，格式为 YYYY-MM 或 YYYY-MM-DD，系统按月份保存；应用场景可选填多个，用逗号分隔。",
                            "备注选填；项目负责人必须使用统一字段，不再接受旧人员列。");
            for (int index = 0; index < notes.size(); index++) {
                var row = instructions.createRow(index);
                var cell = row.createCell(0);
                cell.setCellValue(notes.get(index));
                cell.setCellStyle(style);
            }
            instructions.setColumnWidth(0, 110 * 256);
            return bytes(book);
        } catch (IOException exception) {
            throw new IllegalStateException("生成项目导入模板失败", exception);
        }
    }

    public byte[] failedRows(List<ProjectImportRowEntity> rows) {
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("预检未通过");
            var data = new ArrayList<Map<String, String>>();
            var headers = new LinkedHashSet<>(ProjectExcelReader.mappableHeaders());
            var extraHeaders = new TreeSet<String>();
            for (var row : rows) {
                if (!"FAILED".equals(row.status())) continue;
                Map<String, String> cells = row.sourcePayload() == null ? Map.of()
                        : json.readValue(row.sourcePayload(), new TypeReference<>() { });
                data.add(cells);
                extraHeaders.addAll(cells.keySet());
            }
            headers.addAll(extraHeaders);
            var columns = new ArrayList<String>();
            columns.add("Excel行号");
            columns.add("错误原因");
            columns.addAll(headers);
            writeHeader(book, sheet, columns);
            int index = 0;
            for (var row : rows) {
                if (!"FAILED".equals(row.status())) continue;
                var cells = data.get(index++);
                var target = sheet.createRow(index);
                target.createCell(0).setCellValue(row.rowNumber());
                target.createCell(1).setCellValue(String.join("；",
                        json.readValue(row.errors(), new TypeReference<List<String>>() { })));
                for (int column = 2; column < columns.size(); column++) {
                    target.createCell(column)
                            .setCellValue(cells.getOrDefault(columns.get(column), ""));
                }
            }
            return bytes(book);
        } catch (IOException exception) {
            throw new IllegalStateException("生成项目导入失败行报告失败", exception);
        }
    }

    private void writeHeader(XSSFWorkbook book, org.apache.poi.ss.usermodel.Sheet sheet, List<String> headers) {
        var font = book.createFont();
        font.setFontName("Arial");
        font.setBold(true);
        var style = book.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var row = sheet.createRow(0);
        for (int index = 0; index < headers.size(); index++) {
            var cell = row.createCell(index);
            cell.setCellValue(headers.get(index));
            cell.setCellStyle(style);
            sheet.setColumnWidth(index, Math.min(38, Math.max(18, headers.get(index).length() * 2)) * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private byte[] bytes(XSSFWorkbook book) throws IOException {
        try (var output = new ByteArrayOutputStream()) {
            book.write(output);
            return output.toByteArray();
        }
    }
}
