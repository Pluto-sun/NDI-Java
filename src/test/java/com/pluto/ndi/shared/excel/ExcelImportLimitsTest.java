package com.pluto.ndi.shared.excel;

import com.pluto.ndi.projects.util.ProjectExcelReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelImportLimitsTest {
    @Test
    void readersAcceptWorkbooksBeyondTheFormerRowLimit() throws Exception {
        var mapping = Map.of("项目名称", "项目名称");
        for (int count : List.of(2001, 4200)) {
            var bytes = workbook(count);
            assertThat(new ProjectExcelReader().read(bytes)).hasSize(count);
            assertThat(ExcelWorkbook.read(bytes, "导入数据", 1, mapping, List.of("项目名称"))).hasSize(count);
        }
    }

    @Test
    void acceptsExactlyTwentyMebibytesAndRejectsAnExtraByte() throws Exception {
        var original = workbook(1);
        int extra = ExcelImportLimits.MAX_BYTES - paddedWorkbook(original, 0).length;
        var resized = paddedWorkbook(original, extra);
        assertThat(resized).hasSize(ExcelImportLimits.MAX_BYTES);
        assertThat(ExcelWorkbook.inspect("导入.xlsx", resized, List.of()).sheets()).hasSize(1);
        assertThatThrownBy(() -> ExcelWorkbook.inspect("导入.xlsx",
                Arrays.copyOf(resized, resized.length + 1), List.of()))
                .hasMessageContaining("20 MB");
    }

    private static byte[] paddedWorkbook(byte[] original, int paddingBytes) throws Exception {
        try (var input = new ZipInputStream(new ByteArrayInputStream(original));
                var output = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(output)) {
            for (ZipEntry item; (item = input.getNextEntry()) != null;) {
                zip.putNextEntry(new ZipEntry(item.getName()));
                input.transferTo(zip);
            }
            // 未引用的 XML 项使用存储模式，确保填充长度精确且不触发解压比限制。
            var padding = new byte[paddingBytes];
            Arrays.fill(padding, (byte) ' ');
            var crc = new CRC32();
            crc.update(padding);
            var part = new ZipEntry("xl/padding.xml");
            part.setMethod(ZipEntry.STORED);
            part.setSize(paddingBytes);
            part.setCompressedSize(paddingBytes);
            part.setCrc(crc.getValue());
            zip.putNextEntry(part);
            zip.write(padding);
            zip.finish();
            return output.toByteArray();
        }
    }

    private static byte[] workbook(int rows) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("导入数据");
            var header = sheet.createRow(0);
            var fields = new java.util.ArrayList<>(ProjectExcelReader.requiredHeaders());
            fields.add(com.pluto.ndi.projects.util.ProjectImportAmounts.fields().getFirst());
            for (int i = 0; i < fields.size(); i++) header.createCell(i).setCellValue(fields.get(i));
            for (int i = 1; i <= rows; i++) {
                sheet.createRow(i).createCell(fields.indexOf("项目名称")).setCellValue("项目 " + i);
            }
            book.write(output);
            return output.toByteArray();
        }
    }
}
