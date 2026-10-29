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
    private final List<String> industries = IntStream.rangeClosed(1, 41)
            .mapToObj(index -> "系统行业分类第" + index + "项").toList();

    private byte[] template() {
        var reference = new ProjectReferenceData(
                List.of(new ProvinceOption(guangdong, "广东省"), new ProvinceOption(zhejiang, "浙江省")),
                List.of(new CityOption(UUID.randomUUID(), guangdong, "广州市"),
                        new CityOption(UUID.randomUUID(), guangdong, "深圳市"),
                        new CityOption(UUID.randomUUID(), zhejiang, "杭州市")),
                industries.stream().map(name -> new ProjectReferenceData.CodeLabel(name, name)).toList(),
                List.of(), List.of(new ProjectReferenceData.CodeLabel("SOLUTION_EXCHANGE", "方案交流"),
                        new ProjectReferenceData.CodeLabel("TERMINATED", "已终止")), List.of());
        return new ProjectImportWorkbookWriter(new ObjectMapper()).template(reference);
    }

    @Test
    void writesNativeDropdownsAcrossAllThousandRowsWithLongDictionaryOptions() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template()))) {
            var sheet = book.getSheet("导入数据");
            assertThat(sheet.getDataValidations()).hasSize(5);
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
                assertThat(area.getLastRow()).isEqualTo(1000);
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
