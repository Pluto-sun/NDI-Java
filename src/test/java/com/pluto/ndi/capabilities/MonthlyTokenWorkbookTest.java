package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.excel.ExcelWorkbook;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyTokenWorkbookTest {

    @Test
    void pairsRepeatedScreenshotColumnsAndKeepsZero() throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("月度用量");
            var header = sheet.createRow(0);
            String[] titles = {"省分", "智能体名称", "10月Token量（万）", "Token量-截图",
                    "11月Token量（万）", "Token量-截图", "12月Token量（万）", "Token量-截图"};
            for (int i = 0; i < titles.length; i++) header.createCell(i).setCellValue(titles[i]);

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("广东省");
            row.createCell(1).setCellValue("测试智能体");
            row.createCell(2).setCellValue(0);
            row.createCell(3).setCellFormula("_xlfn.DISPIMG(\"ID_PNG\",1)");
            row.createCell(4).setCellValue(10.25);
            row.createCell(5).setCellValue("测试说明");

            var parts = book.getPackage();
            var images = parts.createPart(PackagingURIHelper.createPartName("/xl/cellimages.xml"), "application/xml");
            var png = PackagingURIHelper.createPartName("/xl/media/token.png");
            var media = parts.createPart(png, "image/png");
            images.addRelationship(png, TargetMode.INTERNAL,
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image", "rId1");
            try (var stream = images.getOutputStream()) {
                stream.write("""
                        <cellImages xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <pic><cNvPr name="ID_PNG"/><blip r:embed="rId1"/></pic>
                        </cellImages>
                        """.getBytes(StandardCharsets.UTF_8));
            }
            var image = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lfoAAAAASUVORK5CYII=");
            try (var stream = media.getOutputStream()) {
                stream.write(image);
            }
            book.write(out);

            var bytes = out.toByteArray();
            var mainFields = List.of("省分", "智能体名称");
            var inspection = ExcelWorkbook.inspect("样例.xlsx", bytes, mainFields,
                    title -> !MonthlyTokenWorkbook.isMonthlyColumn(title));
            assertThat(inspection.sheets().getFirst().headerCandidates().getFirst().headers())
                    .containsExactlyElementsOf(mainFields);
            var mainRows = ExcelWorkbook.read(bytes, "月度用量", 1,
                    Map.of("省分", "省分", "智能体名称", "智能体名称"), mainFields, Set.of(),
                    title -> !MonthlyTokenWorkbook.isMonthlyColumn(title));
            assertThat(mainRows).hasSize(1);
            assertThat(mainRows.getFirst().cells()).containsEntry("智能体名称", "测试智能体");

            var result = MonthlyTokenWorkbook.read(bytes, null);
            assertThat(result.lines()).hasSize(2);
            var first = result.lines().getFirst();
            assertThat(first.month()).isEqualTo(10);
            assertThat(first.amount()).isEqualTo("0");
            assertThat(first.image().type()).isEqualTo("image/png");
            assertThat(first.image().data()).isEqualTo(image);
            var second = result.lines().get(1);
            assertThat(second.month()).isEqualTo(11);
            assertThat(second.warning()).contains("测试说明");
        }
    }

    @Test
    void normalizesOnlyMonthsWithYearsAndValidIsoDates() {
        assertThat(IntelligenceImport.normalizeMonth("2026-08")).isEqualTo("2026-08");
        assertThat(IntelligenceImport.normalizeMonth("2026-08-01")).isEqualTo("2026-08");
        assertThat(IntelligenceImport.normalizeMonth("2026年8月")).isEqualTo("2026-08");
        assertThat(IntelligenceImport.normalizeMonth("2026/8")).isEqualTo("2026-08");
        assertThat(IntelligenceImport.normalizeMonth("7月")).isEqualTo("7月");
        assertThat(IntelligenceImport.normalizeMonth("2026-08-99")).isEqualTo("2026-08-99");
    }
}
