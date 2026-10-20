package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ProjectExcelReaderTest {
    private static final List<String> HEADERS = List.of("省分", "地市", "客户名称", "客户所属行业",
            "项目名称", "省分负责人", "地市客户经理", "项目建设内容", "三化类别",
            "项目阶段", "项目总金额（万）", "网络化预计金额（万）", "格物预计金额（万）",
            "大数据预计金额（万）", "万悟预计金额（万）");

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
