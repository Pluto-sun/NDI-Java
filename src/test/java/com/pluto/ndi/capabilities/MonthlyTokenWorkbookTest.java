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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            var exported = ExcelWorkbook.originalRows(bytes, result.sheet(), 1,
                    Map.of(first.row(), List.of("2026-10：测试失败")), List.of(
                            new ExcelWorkbook.ExportPicture(first.row(), first.screenshotColumn(),
                                    org.apache.poi.ss.usermodel.Workbook.PICTURE_TYPE_PNG, first.image().data())));
            try (var failedBook = new XSSFWorkbook(new java.io.ByteArrayInputStream(exported))) {
                assertThat(failedBook.getAllPictures()).hasSize(1);
                assertThat(failedBook.getAllPictures().getFirst().getData()).isEqualTo(image);
            }
            var second = result.lines().get(1);
            assertThat(second.month()).isEqualTo(11);
            assertThat(second.warning()).contains("测试说明");
        }
    }

    @Test
    void countsWorkbookRowsSeparatelyFromMonthsAndAllowsLargeEmbeddedImages() throws Exception {
        var content = tokenWorkbook(2000, true);
        assertThat(content.length).isBetween(10 * 1024 * 1024 + 1, 20 * 1024 * 1024);
        var accepted = MonthlyTokenWorkbook.read(content, null);
        assertThat(accepted.lines()).hasSize(2001);
        assertThat(accepted.lines().getFirst().image().data()).hasSize(11 * 1024 * 1024);
        assertThat(MonthlyTokenWorkbook.read(tokenWorkbook(2001, false), null).lines()).hasSize(2002);
    }

    private static byte[] tokenWorkbook(int rows, boolean withImage) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("月度用量");
            var header = sheet.createRow(0);
            var titles = List.of("系统ID", "10月Token量（万）", "Token量-截图", "11月Token量（万）");
            for (int i = 0; i < titles.size(); i++) header.createCell(i).setCellValue(titles.get(i));
            for (int i = 1; i <= rows; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("项目 " + i);
                row.createCell(1).setCellValue(0);
            }
            sheet.getRow(1).createCell(3).setCellValue(1);
            if (withImage) {
                sheet.getRow(1).createCell(2).setCellFormula("_xlfn.DISPIMG(\"ID_BIG\",1)");
                var parts = book.getPackage();
                var images = parts.createPart(PackagingURIHelper.createPartName("/xl/cellimages.xml"), "application/xml");
                var png = PackagingURIHelper.createPartName("/xl/media/large.png");
                var media = parts.createPart(png, "image/png");
                images.addRelationship(png, TargetMode.INTERNAL,
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image", "rId1");
                try (var stream = images.getOutputStream()) {
                    stream.write("""
                            <cellImages xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                              <pic><cNvPr name="ID_BIG"/><blip r:embed="rId1"/></pic>
                            </cellImages>
                            """.getBytes(StandardCharsets.UTF_8));
                }
                // 此处验证工作簿解包边界，使用固定随机数据避免 ZIP 压缩弱化测试。
                var data = new byte[11 * 1024 * 1024];
                new java.util.Random(20261119).nextBytes(data);
                var signature = java.util.HexFormat.of().parseHex("89504e470d0a1a0a");
                System.arraycopy(signature, 0, data, 0, signature.length);
                try (var stream = media.getOutputStream()) {
                    stream.write(data);
                }
            }
            book.write(out);
            return out.toByteArray();
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
