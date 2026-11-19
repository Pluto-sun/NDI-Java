package com.pluto.ndi.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyTokenImportTest {
    @Test
    void exportsOneOriginalRowForAllFailedMonthsAndChecksPreviewVersion() throws Exception {
        var actor = UUID.randomUUID();
        var id = UUID.randomUUID();
        var service = mock(IntelligenceService.class);
        when(service.matchTokenAgents(actor, "", "", "", id.toString())).thenReturn(List.of(
                new IntelligenceService.Entry(id, UUID.randomUUID(), null, 0, null, Map.of("省分", "广东省"))));
        when(service.tokens(actor, id)).thenReturn(List.of(
                new IntelligenceService.Token(UUID.randomUUID(), "2026-11", "3", null)));
        var imports = new MonthlyTokenImport(service, new ObjectMapper());
        var file = workbook(id);
        var preview = imports.preview(actor, file, null, 2026);
        assertThat(preview.rows()).extracting(MonthlyTokenImport.Row::status)
                .containsExactly("FAILED", "FAILED", "READY", "FAILED", "SKIPPED");
        var bytes = imports.originalFailedRows(actor, file, preview.sheet(), 2026, preview.fingerprint());
        try (var exported = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = exported.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("预检失败原因");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("不合法数值");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("0");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("用户原文");
            var reasons = sheet.getRow(1).getCell(6).getStringCellValue();
            assertThat(reasons).startsWith("2026-09：").contains("；2026-10：文件内存在同一智能体同月的重复记录")
                    .doesNotContain("2026-12");
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue())
                    .isEqualTo("2026-10：文件内存在同一智能体同月的重复记录");
        }
        when(service.tokens(actor, id)).thenReturn(List.of(
                new IntelligenceService.Token(UUID.randomUUID(), "2026-12", "99", null)));
        assertThatThrownBy(() -> imports.originalFailedRows(actor, file, preview.sheet(), 2026, preview.fingerprint()))
                .hasMessageContaining("重新预检");
        assertThatThrownBy(() -> imports.originalFailedRows(UUID.randomUUID(), file, preview.sheet(), 2026, preview.fingerprint()))
                .hasMessageContaining("重新预检");
    }

    private byte[] workbook(UUID agent) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("月度用量");
            var cells = List.of(
                    List.of("系统ID", "9月Token量（万）", "10月Token量（万）", "12月Token量（万）",
                            "11月Token量（万）", "失败原因"),
                    List.of(agent.toString(), "不合法数值", "1", "0", "", "用户原文"),
                    List.of(agent.toString(), "", "2"),
                    List.of(agent.toString(), "", "", "", "3"));
            for (int i = 0; i < cells.size(); i++) {
                var row = sheet.createRow(i);
                for (int j = 0; j < cells.get(i).size(); j++) row.createCell(j).setCellValue(cells.get(i).get(j));
            }
            book.write(bytes);
            return bytes.toByteArray();
        }
    }
}
