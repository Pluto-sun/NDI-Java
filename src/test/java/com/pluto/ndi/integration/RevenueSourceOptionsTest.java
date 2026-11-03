package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.revenue.service.RevenueService;
import com.pluto.ndi.revenue.util.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.*;

class RevenueSourceOptionsTest {
    private final RevenueFields fields = new RevenueFields(mock(OrganizationDirectory.class));

    Map<String, String> source() {
        return new LinkedHashMap<>(
                Map.of(
                        "月账期",
                        "202611",
                        "省份名称",
                        "数科",
                        "合同编号",
                        "HT001",
                        "条款编号",
                        "001",
                        "条款金额",
                        "100",
                        RevenueFields.SOURCE_AMOUNT,
                        "10"));
    }

    @Test
    void noSignatureDateIsOptionalAndExplicitPlaceholderMeansNoDate() {
        var input = source();
        for (String value : List.of("", "--", "  --  ")) {
            input.put("审签日期", value);
            assertThat(fields.normalize(input).fields()).containsEntry("审签日期", "");
        }
        input.put("审签日期", "2026-11-03");
        assertThat(fields.normalize(input).fields()).containsEntry("审签日期", "2026-11-03");
        input.put("审签日期", "2026-02-30");
        assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("审签日期");
    }

    @Test
    void digitalTechFillsOnlyTheFixedOrganizationWithoutChangingGenericOrganizationRules() {
        var result = fields.normalize(source());
        assertThat(result.organization()).isTrue();
        assertThat(result.fields())
                .containsEntry("地市/组织名称", "联通数字科技有限公司本部")
                .containsEntry("省份名称", "数科");
        var input = source();
        input.put("地市/组织名称", "其他公司");
        assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("仅允许选择");
        input.put("省份名称", "具体组织");
        assertThat(fields.normalize(input).fields())
                .containsEntry("地市/组织名称", "其他公司")
                .containsEntry("省份名称", "具体组织");
    }

    @Test
    void templateOffersDigitalTechAndConditionalOrganizationDropdownAndAcceptsUndatedExcel()
            throws Exception {
        var reference =
                new RevenueService.Reference(
                        List.of(),
                        RevenueFields.FIELDS,
                        RevenueFields.REQUIRED.stream().toList(),
                        true,
                        RevenueSourceOptions.FIXED_ORGANIZATIONS);
        byte[] template = RevenueExcel.template(reference);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            var sheet = book.getSheetAt(0);
            var validations = sheet.getDataValidations();
            assertThat(validations).hasSize(2);
            var province = validations.getFirst().getValidationConstraint().getFormula1();
            var range =
                    new org.apache.poi.ss.util.AreaReference(
                            book.getName(province).getRefersToFormula(),
                            org.apache.poi.ss.SpreadsheetVersion.EXCEL2007);
            assertThat(
                            Arrays.stream(range.getAllReferencedCells())
                                    .map(
                                            c ->
                                                    book.getSheet(c.getSheetName())
                                                            .getRow(c.getRow())
                                                            .getCell(c.getCol())
                                                            .getStringCellValue()))
                    .contains("数科", "具体组织");
            assertThat(validations.get(1).getValidationConstraint().getFormula1())
                    .contains("$C2", "数科", "$D2");
            var row = sheet.createRow(1);
            var input = source();
            input.put("审签日期", "--");
            for (int col = 0; col < RevenueFields.FIELDS.size(); col++)
                row.createCell(col)
                        .setCellValue(input.getOrDefault(RevenueFields.FIELDS.get(col), ""));
            try (var out = new java.io.ByteArrayOutputStream()) {
                book.write(out);
                var mapping = new LinkedHashMap<String, String>();
                RevenueFields.FIELDS.forEach(f -> mapping.put(f, f));
                var lines =
                        RevenueExcel.read(
                                out.toByteArray(),
                                sheet.getSheetName(),
                                1,
                                mapping,
                                RevenueFields.FIELDS);
                assertThat(fields.normalize(lines.getFirst().cells()).fields())
                        .containsEntry("审签日期", "")
                        .containsEntry("地市/组织名称", "联通数字科技有限公司本部");
            }
        }
    }
}
