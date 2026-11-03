package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.revenue.util.RevenueSourceNormalizer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class RevenueSourceNormalizerTest {
    @Test
    void numericIntegerClausesLoseOnlyLeadingZerosWithoutOverflow() {
        var examples =
                Map.of(
                        "002", "2", "000", "0", "0", "0", "000200", "200", "--", "--", "A002",
                        "A002", "2-01", "2-01", "2.01", "2.01");
        examples.forEach(
                (raw, expected) ->
                        assertThat(RevenueSourceNormalizer.clauseNumber(raw)).isEqualTo(expected));
        assertThat(RevenueSourceNormalizer.clauseNumber("0".repeat(150) + "9".repeat(50)))
                .isEqualTo("9".repeat(50));
    }

    @Test
    void calendarDatesAndCommonDateTimesShareTheSameOutputDate() {
        for (var raw :
                List.of(
                        "2026-07-20",
                        "2026/7/20",
                        "2026-07-20 00:00:00",
                        "2026/7/20 0:00:00",
                        "2026-7-20 9:5",
                        "2026-07-20T23:59:59.123456789+08:00",
                        "2026-07-20T00:00:00Z",
                        "2026/7/20 0:00:00.123",
                        " 2026/7/20   0:00:00 "))
            assertThat(RevenueSourceNormalizer.signatureDate(raw)).as(raw).isEqualTo("2026-07-20");
        for (var empty : List.of("", "--", "  --  "))
            assertThat(RevenueSourceNormalizer.signatureDate(empty)).isEmpty();
        assertThat(RevenueSourceNormalizer.signatureDate("2024/2/29 12:00:00"))
                .isEqualTo("2024-02-29");
    }

    @Test
    void malformedDatesAndIllegalTimesCannotBeSilentlyTruncated() {
        for (var raw :
                List.of(
                        "2026-02-30",
                        "2026/2/29",
                        "2026-13-20",
                        "2026-07-20 25:00:00",
                        "2026/7/20 0:60:00",
                        "2026-07-20T00:00:60",
                        "2026-07-20T00:00:00+19:00",
                        "2026-07-20 rubbish",
                        "2026-07/20",
                        "2026-07-20Z"))
            assertThatThrownBy(() -> RevenueSourceNormalizer.signatureDate(raw))
                    .as(raw)
                    .hasMessageContaining("审签日期");
    }

    @Test
    void numericExcelClauseFormattingDoesNotChangeOtherIdentifiersOrCalendarDates()
            throws Exception {
        var headers =
                List.of(
                        "月账期",
                        "省份名称",
                        "合同编号",
                        "条款编号",
                        "条款金额",
                        com.pluto.ndi.revenue.util.RevenueFields.SOURCE_AMOUNT,
                        "审签日期",
                        "业务号码/项目编号");
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.createSheet("来源数据");
            var header = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++)
                header.createCell(col).setCellValue(headers.get(col));
            var row = sheet.createRow(1);
            String[] values = {"202607", "数科", "000001", "", "100", "10", "", ""};
            for (int col = 0; col < values.length; col++)
                row.createCell(col).setCellValue(values[col]);
            row.getCell(3).setCellValue(2);
            var clauseStyle = book.createCellStyle();
            clauseStyle.setDataFormat(book.createDataFormat().getFormat("000.00"));
            row.getCell(3).setCellStyle(clauseStyle);
            row.getCell(7).setCellValue(42);
            var businessStyle = book.createCellStyle();
            businessStyle.setDataFormat(book.createDataFormat().getFormat("000000"));
            row.getCell(7).setCellStyle(businessStyle);
            row.getCell(6).setCellValue(java.time.LocalDateTime.of(2026, 7, 20, 0, 0));
            var dateStyle = book.createCellStyle();
            dateStyle.setDataFormat(book.createDataFormat().getFormat("yyyy/m/d h:mm:ss"));
            row.getCell(6).setCellStyle(dateStyle);
            book.write(out);
            var mapping = new java.util.LinkedHashMap<String, String>();
            headers.forEach(h -> mapping.put(h, h));
            var line =
                    com.pluto.ndi.revenue.util.RevenueExcel.read(
                                    out.toByteArray(),
                                    "来源数据",
                                    1,
                                    mapping,
                                    com.pluto.ndi.revenue.util.RevenueFields.FIELDS)
                            .getFirst();
            var fields =
                    new com.pluto.ndi.revenue.util.RevenueFields(
                            org.mockito.Mockito.mock(
                                    com.pluto.ndi.organization.service.OrganizationDirectory
                                            .class));
            assertThat(fields.normalize(line.cells()).fields())
                    .containsEntry("条款编号", "2")
                    .containsEntry("合同编号", "000001")
                    .containsEntry("业务号码/项目编号", "000042")
                    .containsEntry("审签日期", "2026-07-20");
        }
    }
}
