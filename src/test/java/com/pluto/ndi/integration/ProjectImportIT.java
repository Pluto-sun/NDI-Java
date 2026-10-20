package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.projects.service.ProjectImportService;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.shared.web.BusinessException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class ProjectImportIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }

    @Autowired private ProjectImportService imports;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private DocumentDirectory documentDirectory;
    @MockitoBean private ExportFileDirectory exportFileDirectory;

    @Test
    void keepsFirstDuplicateAndRejectsMissingAmountThenConfirmsOnce() throws Exception {
        UUID province = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,?,true)",
                province.toString(), province.toString().replace("-", ""), "广东省");
        jdbc.update("INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(), province.toString(), city.toString().replace("-", ""), "广州市");
        addUser(actor, province, city, "city-test", "地市导入人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-test", "省分负责人", "PROVINCE_REPORTER");
        int existingProjects = jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class);
        int existingProgress = jdbc.queryForObject("SELECT COUNT(*) FROM project_progress", Integer.class);
        int existingSources = jdbc.queryForObject("SELECT COUNT(*) FROM project_source_record", Integer.class);

        var preview = imports.preview(actor, "广东项目.xlsx", workbook());
        assertThat(preview.totalRows()).isEqualTo(3);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.failedRows()).isEqualTo(2);
        assertThat(preview.rows().get(1).errorsJson()).contains("重复项目");
        assertThat(preview.rows().get(2).errorsJson()).contains("项目总金额");
        try (var template = new XSSFWorkbook(new java.io.ByteArrayInputStream(imports.template(actor)));
                var failed = new XSSFWorkbook(new java.io.ByteArrayInputStream(
                        imports.failedRows(actor, preview.batchId())))) {
            assertThat(template.getSheet("导入数据").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("省分");
            assertThat(template.getSheet("填写说明")).isNotNull();
            assertThat(failed.getSheet("预检未通过").getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(failed.getSheet("预检未通过").getRow(1).getCell(1).getStringCellValue())
                    .contains("重复项目");
        }
        var foreignReport = catchThrowable(() -> imports.failedRows(UUID.randomUUID(), preview.batchId()));
        assertThat(((BusinessException) foreignReport).errorCode()).isEqualTo(ProjectErrorCode.IMPORT_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class))
                .isEqualTo(existingProjects);

        var done = imports.confirm(actor, preview.batchId());
        assertThat(done.importedRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class)).isEqualTo(existingProjects + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_progress", Integer.class)).isEqualTo(existingProgress + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_source_record", Integer.class)).isEqualTo(existingSources + 1);
        assertThat(done.rows().getFirst().status()).isEqualTo("IMPORTED");

        var selective = imports.preview(actor, "选择导入.xlsx", workbookWithDistinctNames());
        assertThat(selective.validRows()).isEqualTo(3);
        var invalidSelection = catchThrowable(() -> imports.confirm(actor, selective.batchId(), List.of(2, 2)));
        assertThat(((BusinessException) invalidSelection).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_SELECTION_INVALID);
        var missingRow = catchThrowable(() -> imports.confirm(actor, selective.batchId(), List.of(2, 999)));
        assertThat(((BusinessException) missingRow).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_SELECTION_INVALID);
        assertThat(imports.find(actor, selective.batchId()).status()).isEqualTo("PREVIEWED");

        var selected = imports.confirm(actor, selective.batchId(), List.of(2, 4));
        assertThat(selected.importedRows()).isEqualTo(2);
        assertThat(selected.skippedRows()).isEqualTo(1);
        assertThat(selected.failedRows()).isZero();
        assertThat(selected.rows().get(1).status()).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class)).isEqualTo(existingProjects + 3);
        assertThat(imports.findMine(actor, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
        var foreign = catchThrowable(() -> imports.find(UUID.randomUUID(), selective.batchId()));
        assertThat(((BusinessException) foreign).errorCode()).isEqualTo(ProjectErrorCode.IMPORT_NOT_FOUND);

        var cancellable = imports.preview(actor, "取消计划.xlsx", workbookWithDistinctNames());
        var foreignCancel = catchThrowable(() -> imports.cancel(UUID.randomUUID(), cancellable.batchId()));
        assertThat(((BusinessException) foreignCancel).errorCode()).isEqualTo(ProjectErrorCode.IMPORT_NOT_FOUND);
        var cancelled = imports.cancel(actor, cancellable.batchId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class)).isEqualTo(existingProjects + 3);
        var cannotConfirm = catchThrowable(() -> imports.confirm(actor, cancelled.batchId(), List.of(2)));
        assertThat(((BusinessException) cannotConfirm).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_NOT_PREVIEWED);
    }

    @Test
    void revisesFailedRowsWithAuditBeforeConfirmation() throws Exception {
        UUID province = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        String provinceName = "修正" + province.toString().substring(0, 8) + "省";
        String cityName = "修正" + city.toString().substring(0, 8) + "市";
        jdbc.update("INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,?,true)",
                province.toString(), province.toString().replace("-", ""), provinceName);
        jdbc.update("INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(), province.toString(), city.toString().replace("-", ""), cityName);
        addUser(actor, province, city, "editor-" + actor, "导入修正人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-" + leader, "省分负责人", "PROVINCE_REPORTER");

        var preview = imports.preview(actor, "待修正.xlsx", workbookWithLocation(provinceName, cityName));
        var batchId = preview.batchId();
        var mappedFile = mappedWorkbook(provinceName, cityName);
        var inspection = imports.inspect(actor, "外部台账.xlsx", mappedFile);
        assertThat(inspection.sheets().getFirst().headerCandidates().get(1).rowNo()).isEqualTo(2);
        var mappedPreview = imports.preview(actor, "外部台账.xlsx", mappedFile,
                "外部台账", 2, Map.of("省分", "省份", "项目名称", "商机名称"));
        assertThat(mappedPreview.validRows()).isEqualTo(1);
        assertThat(mappedPreview.rows().getFirst().rowNumber()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT sheet_name FROM project_import_batch WHERE id = UUID_TO_BIN(?)",
                String.class, mappedPreview.batchId().toString())).isEqualTo("外部台账");
        assertThat(preview.rows().get(2).sourceCellsJson()).contains("项目总金额（万）");
        var missingAmount = imports.reviseRow(actor, batchId, 4, 0,
                Map.of("项目总金额（万）", "100"));
        assertThat(missingAmount.validRows()).isEqualTo(2);
        assertThat(missingAmount.rows().get(2).revisionNo()).isEqualTo(1);
        var renamedDuplicate = imports.reviseRow(actor, batchId, 3, 0,
                Map.of("项目名称", "第二条项目"));
        assertThat(renamedDuplicate.validRows()).isEqualTo(3);
        assertThat(renamedDuplicate.failedRows()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_import_row_revision", Integer.class))
                .isGreaterThanOrEqualTo(2);

        var stale = catchThrowable(() -> imports.reviseRow(actor, batchId, 4, 0,
                Map.of("项目名称", "旧版本")));
        assertThat(((BusinessException) stale).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_ROW_VERSION_CONFLICT);
        var unknown = catchThrowable(() -> imports.reviseRow(actor, batchId, 4, 1,
                Map.of("非模板字段", "值")));
        assertThat(((BusinessException) unknown).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_ROW_INVALID);
        var foreign = catchThrowable(() -> imports.reviseRow(UUID.randomUUID(), batchId, 4, 1,
                Map.of("项目名称", "越权")));
        assertThat(foreign).isInstanceOf(BusinessException.class);

        var done = imports.confirm(actor, batchId);
        assertThat(done.importedRows()).isEqualTo(3);
        var afterConfirmation = catchThrowable(() -> imports.reviseRow(actor, batchId, 4, 1,
                Map.of("项目名称", "确认后修改")));
        assertThat(((BusinessException) afterConfirmation).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_ROW_NOT_EDITABLE);
    }

    @Test
    void recoversInterruptedConfirmationWithoutReimportingCompletedRows() throws Exception {
        UUID province = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        String provinceName = "恢复" + province.toString().substring(0, 8) + "省";
        String cityName = "恢复" + city.toString().substring(0, 8) + "市";
        jdbc.update("INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,?,true)",
                province.toString(), province.toString().replace("-", ""), provinceName);
        jdbc.update("INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(), province.toString(), city.toString().replace("-", ""), cityName);
        addUser(actor, province, city, "recover-" + actor, "导入恢复人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-" + leader, "省分负责人", "PROVINCE_REPORTER");
        var preview = imports.preview(actor, "待恢复.xlsx", workbookWithLocation(provinceName, cityName));
        jdbc.update("UPDATE project_import_batch SET status = 'CONFIRMING', confirmed_at = DATE_SUB(NOW(6), INTERVAL 16 MINUTE) WHERE id = UUID_TO_BIN(?)",
                preview.batchId().toString());

        imports.recoverStale();
        var recovered = imports.find(actor, preview.batchId());
        assertThat(recovered.status()).isEqualTo("COMPLETED");
        assertThat(recovered.importedRows()).isEqualTo(1);
        assertThat(recovered.failedRows()).isEqualTo(2);
        assertThat(recovered.rows().getFirst().status()).isEqualTo("IMPORTED");
        imports.recoverStale();
        assertThat(imports.find(actor, preview.batchId()).importedRows()).isEqualTo(1);
    }

    private void addUser(UUID id, UUID province, UUID city, String login, String name, String role) {
        jdbc.update("""
                INSERT INTO identity_user_account(id,login_name,display_name,province_id,city_id,enabled)
                VALUES (UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),?,true)
                """, id.toString(), login, name, province.toString(), city == null ? null : uuidBytes(city));
        jdbc.update("INSERT INTO identity_user_role(user_id,role_code) VALUES (UUID_TO_BIN(?),?)", id.toString(), role);
    }

    private byte[] uuidBytes(UUID value) {
        var buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private byte[] workbook() throws Exception {
        var headers = List.of("省分", "地市", "客户名称", "客户所属行业", "项目名称",
                "省分负责人", "地市客户经理", "项目建设内容", "三化类别", "项目阶段",
                "项目总金额（万）", "网络化预计金额（万）", "格物预计金额（万）",
                "大数据预计金额（万）", "万悟预计金额（万）", "项目进展（2026年10月10日）");
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入数据");
            for (int rowIndex = 0; rowIndex <= 3; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                var values = rowIndex == 0 ? headers : List.of("广东省", "广州市", "某客户",
                        "计算机、通信和其他电子设备制造业", rowIndex == 3 ? "缺金额项目" : "首条项目",
                        "省分负责人", "张经理", "客户需要建设平台", "数字化（格物）", "方案交流",
                        rowIndex == 3 ? "" : "100", "", "50", "", "", "完成交流");
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithDistinctNames() throws Exception {
        try (var source = new XSSFWorkbook(new java.io.ByteArrayInputStream(workbook()));
                var output = new ByteArrayOutputStream()) {
            var sheet = source.getSheet("导入数据");
            for (int row = 1; row <= 3; row++) {
                sheet.getRow(row).getCell(4).setCellValue("待导入项目" + row);
            }
            sheet.getRow(3).getCell(10).setCellValue("100");
            source.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithLocation(String provinceName, String cityName) throws Exception {
        try (var source = new XSSFWorkbook(new java.io.ByteArrayInputStream(workbook()));
                var output = new ByteArrayOutputStream()) {
            var sheet = source.getSheet("导入数据");
            for (int row = 1; row <= 3; row++) {
                sheet.getRow(row).getCell(0).setCellValue(provinceName);
                sheet.getRow(row).getCell(1).setCellValue(cityName);
            }
            source.write(output);
            return output.toByteArray();
        }
    }

    private byte[] mappedWorkbook(String provinceName, String cityName) throws Exception {
        try (var source = new XSSFWorkbook(new java.io.ByteArrayInputStream(
                workbookWithLocation(provinceName, cityName)));
                var output = new ByteArrayOutputStream()) {
            var sheet = source.getSheet("导入数据");
            source.setSheetName(source.getSheetIndex(sheet), "外部台账");
            sheet.shiftRows(0, 3, 1);
            sheet.createRow(0).createCell(0).setCellValue("导入说明");
            sheet.getRow(1).getCell(0).setCellValue("省份");
            sheet.getRow(1).getCell(4).setCellValue("商机名称");
            source.write(output);
            return output.toByteArray();
        }
    }
}
