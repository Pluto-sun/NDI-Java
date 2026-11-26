package com.pluto.ndi.projects.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.organization.service.ProvinceOption;
import com.pluto.ndi.projects.service.ProjectReferenceData;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectImportWorkbookWriterTest {
    private final UUID guangdong = UUID.randomUUID();
    private final UUID zhejiang = UUID.randomUUID();
    private final UUID beijing = UUID.randomUUID();
    private final List<String> industries = IntStream.rangeClosed(1, 41)
            .mapToObj(index -> "系统行业分类第" + index + "项").toList();

    private byte[] template() {
        var reference = new ProjectReferenceData(
                List.of(new ProvinceOption(guangdong, "广东省"), new ProvinceOption(zhejiang, "浙江省"),
                        new ProvinceOption(beijing, "北京市", true)),
                List.of(new CityOption(UUID.randomUUID(), guangdong, "广州市"),
                        new CityOption(UUID.randomUUID(), guangdong, "深圳市"),
                        new CityOption(UUID.randomUUID(), zhejiang, "杭州市"),
                        new CityOption(UUID.randomUUID(), beijing, "北京市", true),
                        new CityOption(UUID.randomUUID(), beijing, "昌平")),
                industries.stream().map(name -> new ProjectReferenceData.CodeLabel(name, name)).toList(),
                List.of(), List.of(new ProjectReferenceData.CodeLabel("SOLUTION_EXCHANGE", "方案交流"),
                        new ProjectReferenceData.CodeLabel("TERMINATED", "已终止")), List.of());
        return new ProjectImportWorkbookWriter(new ObjectMapper()).template(reference);
    }

    @Test
    void writesNativeDropdownsAcrossAllThousandRowsWithLongDictionaryOptions() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var sheet = book.getSheet("导入数据");
            assertThat(sheet.getDataValidations()).hasSize(6);
            assertThat(book.isSheetHidden(book.getSheetIndex("导入选项"))).isTrue();
            for (var validation : sheet.getDataValidations()) {
                assertThat(validation.getValidationConstraint().getValidationType())
                        .isEqualTo(DataValidationConstraint.ValidationType.LIST);
                assertThat(validation.getErrorStyle()).isEqualTo(DataValidation.ErrorStyle.STOP);
                assertThat(validation.getShowErrorBox()).isTrue();
                assertThat(validation.getShowPromptBox()).isTrue();
                assertThat(validation.getSuppressDropDownArrow()).isTrue();
                var area = validation.getRegions().getCellRangeAddresses()[0];
                assertThat(area.getFirstRow()).isEqualTo(1);
                assertThat(area.getLastRow()).isEqualTo(org.apache.poi.ss.SpreadsheetVersion.EXCEL2007.getLastRowIndex());
            }
            for (var rule : sheet.getCTWorksheet().getDataValidations().getDataValidationArray())
                assertThat(rule.getShowDropDown()).isFalse();
            assertThat(options(book, "客户所属行业")).containsExactlyElementsOf(industries);
            assertThat(options(book, "项目阶段")).containsExactly("方案交流", "已终止");
            assertThat(options(book, "签约主体")).containsExactly("省分签约", "数科签约");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }

    @Test
    void cityDropdownRecalculatesFromTheSameRowsProvinceAndHandlesUnselectedProvince() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var sheet = book.getSheet("导入数据");
            var formula = sheet.getDataValidations().stream()
                    .filter(value -> value.getRegions().getCellRangeAddresses()[0].getFirstColumn() == 1)
                    .findFirst().orElseThrow().getValidationConstraint().getFormula1();
            assertThat(formula).contains("$A2").doesNotContain("$A$2");
            var row = sheet.createRow(1);
            var province = row.createCell(0);
            var count = row.createCell(25);
            var first = row.createCell(26);
            count.setCellFormula("COUNTA(" + formula + ")");
            first.setCellFormula("INDEX(" + formula + ",1)");
            var evaluator = book.getCreationHelper().createFormulaEvaluator();
            province.setCellValue("广东省");
            assertThat(evaluator.evaluate(count).getNumberValue()).isEqualTo(2);
            assertThat(evaluator.evaluate(first).getStringValue()).isEqualTo("广州市");
            province.setCellValue("浙江省");
            evaluator.clearAllCachedResultValues();
            assertThat(evaluator.evaluate(count).getNumberValue()).isEqualTo(1);
            assertThat(evaluator.evaluate(first).getStringValue()).isEqualTo("杭州市");
            province.setCellValue("北京市");
            evaluator.clearAllCachedResultValues();
            assertThat(evaluator.evaluate(count).getNumberValue()).isEqualTo(1);
            assertThat(evaluator.evaluate(first).getStringValue()).isEqualTo("昌平");
            province.setCellValue("未启用省份");
            evaluator.clearAllCachedResultValues();
            assertThat(evaluator.evaluate(count).getNumberValue()).isZero();
            province.setBlank();
            evaluator.clearAllCachedResultValues();
            assertThat(evaluator.evaluate(count).getNumberValue()).isZero();
        }
    }

    @Test
    void hiddenDictionariesAreNotOfferedAsImportDataSheets() throws Exception {
        var bytes = template();
        var reader = new ProjectExcelReader();
        assertThat(reader.inspect(bytes, "模板.xlsx").sheets())
                .extracting(ProjectExcelReader.SheetInspection::name)
                .containsExactly("导入数据", "填写说明");
        assertThat(reader.read(bytes)).isEmpty();
    }

    @Test
    void exportsOnlySelectedOriginalRowsWithUnmappedValuesAndNumberFormats() throws Exception {
        try (var source = new XSSFWorkbook(); var bytes = new java.io.ByteArrayOutputStream()) {
            var sheet = source.createSheet("源表");
            sheet.createRow(0).createCell(0).setCellValue("表标题");
            var headers = sheet.createRow(1);
            headers.createCell(0).setCellValue("自定义项目标题");
            headers.createCell(2).setCellValue("未映射编号");
            headers.createCell(3).setCellValue("失败原因");
            headers.createCell(4).setCellValue("预检失败原因");
            headers.createCell(5).setCellValue("原日期");
            sheet.createRow(2).createCell(0).setCellValue("通过的项目");
            var failed = sheet.createRow(4);
            failed.createCell(0).setCellValue("  原始名称  ");
            var code = failed.createCell(2);
            code.setCellValue(7);
            var format = source.createCellStyle();
            format.setDataFormat(source.createDataFormat().getFormat("0000"));
            code.setCellStyle(format);
            failed.createCell(3).setCellValue("用户列");
            failed.createCell(4).setCellValue("另一用户列");
            var date = failed.createCell(5);
            date.setCellValue(java.time.LocalDate.of(2026, 9, 7));
            var dateStyle = source.createCellStyle();
            dateStyle.setDataFormat(source.createDataFormat().getFormat("yyyy/mm/dd"));
            date.setCellStyle(dateStyle);
            failed.createCell(6).setCellValue("超出表头的原值");
            source.write(bytes);

            var exported = new ProjectImportWorkbookWriter(new ObjectMapper())
                    .originalRows(bytes.toByteArray(), "源表", 2,
                            java.util.Map.of(5, List.of("=需按文本保存", "日期无效", "日期无效")));
            try (var result = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
                var output = result.getSheet("源表");
                assertThat(output.getPhysicalNumberOfRows()).isEqualTo(2);
                assertThat(output.getRow(0).getCell(0).getStringCellValue()).isEqualTo("自定义项目标题");
                assertThat(output.getRow(0).getCell(2).getStringCellValue()).isEqualTo("未映射编号");
                assertThat(output.getRow(1).getCell(0).getStringCellValue()).isEqualTo("  原始名称  ");
                var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
                assertThat(formatter.formatCellValue(output.getRow(1).getCell(2))).isEqualTo("0007");
                assertThat(output.getRow(1).getCell(3).getStringCellValue()).isEqualTo("用户列");
                assertThat(output.getRow(1).getCell(4).getStringCellValue()).isEqualTo("另一用户列");
                assertThat(formatter.formatCellValue(output.getRow(1).getCell(5))).isEqualTo("2026/09/07");
                assertThat(output.getRow(1).getCell(6).getStringCellValue()).isEqualTo("超出表头的原值");
                assertThat(output.getRow(0).getCell(7).getStringCellValue()).isEqualTo("预检失败原因（2）");
                var reason = output.getRow(1).getCell(7);
                assertThat(reason.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(reason.getStringCellValue()).isEqualTo("=需按文本保存；日期无效");
                assertThat(reason.getCellStyle().getWrapText()).isTrue();
            }
        }
    }

    private List<String> options(XSSFWorkbook book, String field) {
        int column = ProjectExcelReader.mappableHeaders().indexOf(field);
        var validation = book.getSheet("导入数据").getDataValidations().stream()
                .filter(value -> value.getRegions().getCellRangeAddresses()[0].getFirstColumn() == column)
                .findFirst().orElseThrow();
        var range = new AreaReference(book.getName(validation.getValidationConstraint().getFormula1())
                .getRefersToFormula(), SpreadsheetVersion.EXCEL2007);
        return java.util.Arrays.stream(range.getAllReferencedCells())
                .map(cell -> book.getSheet(cell.getSheetName()).getRow(cell.getRow())
                        .getCell(cell.getCol()).getStringCellValue()).toList();
    }
}
