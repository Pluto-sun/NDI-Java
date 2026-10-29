package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

class ProjectExcelReaderTest {
    private static final List<String> HEADERS =
            List.of(
                    "省分",
                    "地市",
                    "客户名称",
                    "客户所属行业",
                    "项目名称",
                    "项目负责人",
                    "地市客户经理",
                    "项目建设内容",
                    "三化类别",
                    "项目阶段",
                    "项目总金额（万）",
                    "网络化预计金额（万）",
                    "格物预计金额（万）",
                    "大数据预计金额（万）",
                    "万悟预计金额（万）");

    @Test
    void inspectionDoesNotTreatRepeatedDataOrSupportingSheetFormulasAsInvalidHeaders() throws Exception {
        byte[] data;
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("导入数据");
            var header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("广东");
            row.createCell(4).setCellValue("重复项目内容");
            row.createCell(7).setCellValue("重复项目内容");
            row.createCell(10).setCellValue(0);
            row.createCell(11).setCellValue(0);
            book.createSheet("说明").createRow(0).createCell(0).setCellFormula("1+1");
            book.write(output);
            data = output.toByteArray();
        }
        var reader = new ProjectExcelReader();
        assertThat(reader.inspect(data, "多工作表.xlsx").sheets()).hasSize(2);
        assertThat(reader.read(data)).hasSize(1);
    }

    @Test
    void selectedHeaderStillRejectsDuplicateNames() throws Exception {
        byte[] data;
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var row = book.createSheet("导入数据").createRow(0);
            row.createCell(0).setCellValue("省分");
            row.createCell(1).setCellValue("项目名称");
            row.createCell(2).setCellValue("项目名称");
            book.write(output);
            data = output.toByteArray();
        }
        assertThatThrownBy(() -> new ProjectExcelReader().read(data))
                .hasMessageContaining("重复表头");
    }

    @Test
    void readsApprovedTemplateAndPreservesSourceRowNumber() throws Exception {
        var rows = new ProjectExcelReader().read(workbook(false));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().number()).isEqualTo(2);
        assertThat(rows.getFirst().get("项目名称")).isEqualTo("测试项目");
    }

    @Test
    void rejectsFormulaCells() throws Exception {
        assertThatThrownBy(() -> new ProjectExcelReader().read(workbook(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得包含公式");
    }

    @Test
    void inspectsAlternativeSheetAndReadsManuallyMappedHeaders() throws Exception {
        byte[] data;
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("外部台账");
            sheet.createRow(0).createCell(0).setCellValue("项目台账");
            var header = sheet.createRow(1);
            for (int index = 0; index < HEADERS.size(); index++) {
                String name = HEADERS.get(index);
                header.createCell(index).setCellValue(
                        "省分".equals(name) ? "省份" : "项目名称".equals(name) ? "商机名称" : name);
            }
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("广东省");
            row.createCell(2).setCellValue("测试客户");
            row.createCell(4).setCellValue("映射后的项目");
            book.write(output);
            data = output.toByteArray();
        }
        var reader = new ProjectExcelReader();
        var inspection = reader.inspect(data, "外部台账.xlsx");
        assertThat(inspection.sheets().getFirst().name()).isEqualTo("外部台账");
        assertThat(inspection.sheets().getFirst().headerCandidates().get(1).headers())
                .contains("省份", "商机名称");
        var rows = reader.read(data, "外部台账", 2,
                Map.of("省分", "省份", "项目名称", "商机名称"));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().number()).isEqualTo(3);
        assertThat(rows.getFirst().get("省分")).isEqualTo("广东省");
        assertThat(rows.getFirst().get("项目名称")).isEqualTo("映射后的项目");
        var unmapped = reader.read(data, "外部台账", 2,
                Map.of("省分", "省份", "项目名称", "商机名称", "客户名称", ""));
        assertThat(unmapped.getFirst().get("客户名称")).isBlank();
        assertThatThrownBy(() -> reader.read(data, "外部台账", 2,
                Map.of("省分", "省份", "项目名称", "省份")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignoresLegacyCategoryColumnsAndMappings() throws Exception {
        byte[] data;
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("旧模板");
            var header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.size(); index++) {
                header.createCell(index).setCellValue(index == 8 ? "三化类别一" : HEADERS.get(index));
            }
            header.createCell(HEADERS.size()).setCellValue("三化类别二");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("广东省");
            row.createCell(4).setCellValue("旧格式项目");
            row.createCell(8).setCellValue("网络化");
            row.createCell(HEADERS.size()).setCellValue("格物");
            book.write(output);
            data = output.toByteArray();
        }
        var rows =
                new ProjectExcelReader().read(data, "旧模板", 1, Map.of("三化类别", "__CATEGORY_SLOTS__"));
        assertThat(rows.getFirst().cells()).doesNotContainKey("三化类别");
        assertThat(new ProjectExcelReader().inspect(data, "旧模板.xlsx").fields())
                .doesNotContain("三化类别");
        assertThatThrownBy(
                        () ->
                                new ProjectExcelReader()
                                        .read(data, "旧模板", 1, Map.of("项目名称", "__CATEGORY_SLOTS__")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] workbook(boolean formula) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("导入数据");
            var header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("广东省");
            row.createCell(4).setCellValue("测试项目");
            if (formula) row.createCell(10).setCellFormula("1+1");
            book.write(output);
            return output.toByteArray();
        }
    }
}
