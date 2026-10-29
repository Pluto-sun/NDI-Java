package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectImportService;
import com.pluto.ndi.shared.web.BusinessException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class ProjectImportIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }

    @Autowired private ProjectImportService imports;
    @Autowired private com.pluto.ndi.projects.service.ProjectService projectService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private DocumentDirectory documentDirectory;
    @MockitoBean private ExportFileDirectory exportFileDirectory;

    @Test
    void importsUnifiedPeopleWithoutRequiringAnySystemAccount() throws Exception {
        var fixture = contractFixture();
        byte[] bytes;
        try (var book =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
                                        contractWorkbook(fixture.name(), "方案交流", "", "")));
                var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheet("导入数据");
            sheet.getRow(0).createCell(18).setCellValue("项目负责人");
            sheet.getRow(1).createCell(18).setCellValue("外部姓名甲；外部姓名乙;外部姓名甲");
            sheet.getRow(1).getCell(5).setCellValue("");
            sheet.getRow(1).getCell(6).setCellValue("");
            book.write(output);
            bytes = output.toByteArray();
        }
        var preview = imports.preview(fixture.actor(), "统一负责人.xlsx", bytes);
        assertThat(preview.validRows()).isEqualTo(1);
        var imported = imports.confirm(fixture.actor(), preview.batchId());
        assertThat(imported.importedRows()).isEqualTo(1);
        var detail =
                projectService.findDetail(fixture.actor(), imported.rows().getFirst().projectId());
        assertThat(detail.responsiblePersons())
                .extracting(p -> p.name())
                .containsExactly("外部姓名甲", "外部姓名乙", "外部姓名甲");
        assertThat(detail.responsiblePersons())
                .allMatch(p -> p.responsibility() == null && p.userId() == null);
    }

    @Test
    void remappingRequiresSameWorkbookAndZeroImportedRows() throws Exception {
        var fixture = contractFixture();
        byte[] bytes = contractWorkbook(fixture.name(), "方案交流", "", "");
        var original = imports.preview(fixture.actor(), "原文件.xlsx", bytes);
        var wrongFile =
                catchThrowable(
                        () ->
                                imports.preview(
                                        fixture.actor(),
                                        "其他文件.xlsx",
                                        contractWorkbook(fixture.name() + "其他", "方案交流", "", ""),
                                        null,
                                        null,
                                        Map.of(),
                                        original.batchId()));
        assertThat(wrongFile).isInstanceOf(BusinessException.class);
        assertThat(imports.find(fixture.actor(), original.batchId()).status())
                .isEqualTo("PREVIEWED");
        var replacement =
                imports.preview(
                        fixture.actor(),
                        "原文件.xlsx",
                        bytes,
                        null,
                        null,
                        Map.of(),
                        original.batchId());
        assertThat(imports.find(fixture.actor(), original.batchId()).status())
                .isEqualTo("CANCELLED");
        assertThat(imports.find(fixture.actor(), original.batchId()).rows()).isNotEmpty();
        var failed = imports.preview(fixture.actor(), "重复.xlsx", bytes);
        assertThat(imports.confirm(fixture.actor(), replacement.batchId()).importedRows())
                .isEqualTo(1);
        assertThat(
                        catchThrowable(
                                () ->
                                        imports.preview(
                                                fixture.actor(),
                                                "原文件.xlsx",
                                                bytes,
                                                null,
                                                null,
                                                Map.of(),
                                                replacement.batchId())))
                .isInstanceOf(BusinessException.class);
        assertThat(imports.confirm(fixture.actor(), failed.batchId()).importedRows()).isZero();
        assertThat(
                        imports.preview(
                                        fixture.actor(),
                                        "重复.xlsx",
                                        bytes,
                                        null,
                                        null,
                                        Map.of(),
                                        failed.batchId())
                                .status())
                .isEqualTo("PREVIEWED");
    }

    @Test
    void importsCollectionContractsAndRechecksGlobalUniquenessAfterPreview() throws Exception {
        var fixture = contractFixture();
        String contract = "HT-" + UUID.randomUUID();
        var preview =
                imports.preview(
                        fixture.actor(),
                        "计收项目.xlsx",
                        contractWorkbook(fixture.name(), "计收", contract, "80.50"));
        assertThat(preview.validRows()).isEqualTo(1);
        var competing =
                imports.preview(
                        fixture.actor(),
                        "并行计划.xlsx",
                        contractWorkbook(fixture.name(), "计收", contract, "90"));
        assertThat(competing.validRows()).isEqualTo(1);
        var done = imports.confirm(fixture.actor(), preview.batchId());
        assertThat(done.importedRows()).isEqualTo(1);
        var project = done.rows().getFirst().projectId();
        var stored =
                jdbc.queryForMap(
                        "SELECT current_stage_code,contract_no,signed_amount FROM project_project"
                            + " WHERE id=UUID_TO_BIN(?)",
                        project.toString());
        assertThat(stored.get("current_stage_code")).isEqualTo("COLLECTION");
        assertThat(stored.get("contract_no")).isEqualTo(contract);
        assertThat((java.math.BigDecimal) stored.get("signed_amount"))
                .isEqualByComparingTo("80.50");
        var conflict = imports.confirm(fixture.actor(), competing.batchId());
        assertThat(conflict.importedRows()).isZero();
        assertThat(conflict.rows().getFirst().errorsJson()).contains("合同编号已存在");
        assertThat(
                        imports.preview(
                                        fixture.actor(),
                                        "重复合同.xlsx",
                                        contractWorkbook(fixture.name(), "计收", contract, "10"))
                                .rows()
                                .getFirst()
                                .errorsJson())
                .contains("合同编号已存在");
    }

    @Test
    void validatesContractPairsAndPreservesTerminatedContractInformation() throws Exception {
        var fixture = contractFixture();
        var missing =
                imports.preview(
                        fixture.actor(),
                        "缺合同.xlsx",
                        contractWorkbook(fixture.name(), "计收", "", ""));
        assertThat(missing.rows().getFirst().errorsJson()).contains("合同编号不能为空", "签约金额不能为空");
        for (String stage : List.of("方案交流", "招投标")) {
            assertThat(
                            imports.preview(
                                            fixture.actor(),
                                            "提前合同.xlsx",
                                            contractWorkbook(
                                                    fixture.name(),
                                                    stage,
                                                    "HT-" + UUID.randomUUID(),
                                                    "1"))
                                    .rows()
                                    .getFirst()
                                    .errorsJson())
                    .contains("不能填写合同信息");
        }
        assertThat(
                        imports.preview(
                                        fixture.actor(),
                                        "缺金额.xlsx",
                                        contractWorkbook(
                                                fixture.name(),
                                                "签约",
                                                "HT-" + UUID.randomUUID(),
                                                ""))
                                .rows()
                                .getFirst()
                                .errorsJson())
                .contains("签约金额不能为空");
        assertThat(
                        imports.preview(
                                        fixture.actor(),
                                        "非法金额.xlsx",
                                        contractWorkbook(
                                                fixture.name(),
                                                "计收",
                                                "HT-" + UUID.randomUUID(),
                                                "-1"))
                                .rows()
                                .getFirst()
                                .errorsJson())
                .contains("签约金额超出金额范围");
        String contract = "HT-" + UUID.randomUUID();
        var terminated =
                imports.preview(
                        fixture.actor(),
                        "终止保留合同.xlsx",
                        contractWorkbook(fixture.name(), "已终止", contract, "0"));
        assertThat(terminated.validRows()).isEqualTo(1);
        assertThat(imports.confirm(fixture.actor(), terminated.batchId()).importedRows())
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_stage_code FROM project_project WHERE"
                                    + " contract_no=?",
                                String.class,
                                contract))
                .isEqualTo("TERMINATED");
    }

    @Test
    void infersMultipleCategoriesFromAmountsIncludingZeroAndIgnoresLegacyCategoryText()
            throws Exception {
        var fixture = contractFixture();
        byte[] bytes;
        try (var book =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
                                        contractWorkbook(fixture.name(), "方案交流", "", "")));
                var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            sheet.getRow(1).getCell(8).setCellValue("不认识的旧类别");
            sheet.getRow(1).getCell(11).setCellValue("30");
            sheet.getRow(1).getCell(12).setCellValue("0");
            sheet.getRow(1).getCell(13).setCellValue("20.50");
            sheet.getRow(1).getCell(14).setCellValue("");
            book.write(out);
            bytes = out.toByteArray();
        }
        var preview = imports.preview(fixture.actor(), "自动类别.xlsx", bytes);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.rows().getFirst().sourceCellsJson()).doesNotContain("三化类别");
        var done = imports.confirm(fixture.actor(), preview.batchId());
        var detail = projectService.findDetail(fixture.actor(), done.rows().getFirst().projectId());
        assertThat(detail.categoryAmounts())
                .extracting(item -> item.categoryCode())
                .containsExactlyInAnyOrder("NETWORK", "GEWU", "BIG_DATA");
        assertThat(detail.categoryAmounts())
                .anySatisfy(
                        item -> {
                            assertThat(item.categoryCode()).isEqualTo("GEWU");
                            assertThat(item.expectedAmount()).isEqualByComparingTo("0");
                        });
    }

    @Test
    void validatesEveryNonEmptyAmountAndRejectsEmptyCategoriesOrExcessTotal() throws Exception {
        var fixture = contractFixture();
        for (var value : List.of("-1", "0.001", "非法数值", "101")) {
            byte[] bytes;
            try (var book =
                            new XSSFWorkbook(
                                    new java.io.ByteArrayInputStream(
                                            contractWorkbook(fixture.name(), "方案交流", "", "")));
                    var out = new ByteArrayOutputStream()) {
                var sheet = book.getSheetAt(0);
                sheet.getRow(0).getCell(8).setCellValue("忽略的旧类别");
                sheet.getRow(1).getCell(12).setCellValue(value);
                book.write(out);
                bytes = out.toByteArray();
            }
            assertThat(imports.preview(fixture.actor(), "非法金额.xlsx", bytes).failedRows())
                    .isEqualTo(1);
        }
        byte[] empty;
        try (var book =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
                                        contractWorkbook(fixture.name(), "方案交流", "", "")));
                var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            sheet.getRow(0).getCell(8).setCellValue("无需类别列");
            for (int i = 11; i <= 14; i++) sheet.getRow(1).getCell(i).setCellValue("");
            book.write(out);
            empty = out.toByteArray();
        }
        assertThat(
                        imports.preview(fixture.actor(), "空金额.xlsx", empty)
                                .rows()
                                .getFirst()
                                .errorsJson())
                .contains("至少填写一项三化预计金额");
    }

    @Test
    void revalidatesLegacyUnconfirmedRowsWithoutDiscardingCorrections() throws Exception {
        var fixture = contractFixture();
        var preview =
                imports.preview(
                        fixture.actor(),
                        "旧任务.xlsx",
                        contractWorkbook(fixture.name(), "方案交流", "", ""));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var cells =
                json.readValue(
                        preview.rows().getFirst().sourceCellsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                java.util.Map<String, String>>() {});
        cells.put("三化类别", "无效旧类别");
        cells.put("网络化预计金额（万）", "0");
        cells.put("项目名称", "已修正的旧任务项目");
        jdbc.update(
                "UPDATE project_import_row SET source_payload=CAST(? AS JSON) WHERE"
                    + " batch_id=UUID_TO_BIN(?)",
                json.writeValueAsString(cells),
                preview.batchId().toString());
        var restored = imports.find(fixture.actor(), preview.batchId());
        assertThat(restored.validRows()).isEqualTo(1);
        assertThat(restored.rows().getFirst().sourceCellsJson())
                .doesNotContain("三化类别")
                .contains("已修正的旧任务项目");
        var done = imports.confirm(fixture.actor(), preview.batchId());
        assertThat(
                        projectService
                                .findDetail(fixture.actor(), done.rows().getFirst().projectId())
                                .categoryAmounts())
                .hasSize(2);
    }

    private record ContractFixture(UUID actor, String name) {}

    private ContractFixture contractFixture() {
        UUID province = UUID.randomUUID(),
                city = UUID.randomUUID(),
                actor = UUID.randomUUID(),
                leader = UUID.randomUUID();
        String name = "合同导入" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO organization_province(id,province_code,province_name,enabled)"
                    + " VALUES(UUID_TO_BIN(?),?,?,true)",
                province.toString(),
                province.toString().replace("-", ""),
                name);
        jdbc.update(
                "INSERT INTO organization_city(id,province_id,city_code,city_name,enabled)"
                    + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(),
                province.toString(),
                city.toString().replace("-", ""),
                name);
        addUser(actor, province, city, "import-" + actor, "导入人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-" + leader, "省分负责人", "PROVINCE_REPORTER");
        return new ContractFixture(actor, name);
    }

    private byte[] contractWorkbook(String location, String stage, String contract, String amount)
            throws Exception {
        try (var book =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
                                        workbookWithLocation(location, location)));
                var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheet("导入数据");
            sheet.removeRow(sheet.getRow(3));
            sheet.removeRow(sheet.getRow(2));
            sheet.getRow(0).createCell(16).setCellValue("合同编号");
            sheet.getRow(0).createCell(17).setCellValue("签约金额（万）");
            sheet.getRow(1).getCell(4).setCellValue("合同项目" + UUID.randomUUID());
            sheet.getRow(1).getCell(9).setCellValue(stage);
            sheet.getRow(1).createCell(16).setCellValue(contract);
            sheet.getRow(1).createCell(17).setCellValue(amount);
            book.write(output);
            return output.toByteArray();
        }
    }

    @Test
    void keepsFirstDuplicateAndRejectsMissingAmountThenConfirmsOnce() throws Exception {
        UUID province =
                UUID.fromString(
                        jdbc.queryForObject(
                                "SELECT BIN_TO_UUID(id) FROM organization_province WHERE"
                                    + " province_code='440000'",
                                String.class));
        UUID city =
                UUID.fromString(
                        jdbc.queryForObject(
                                "SELECT BIN_TO_UUID(id) FROM organization_city WHERE"
                                    + " city_code='440100'",
                                String.class));
        UUID actor = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        addUser(actor, province, city, "city-test", "地市导入人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-test", "省分负责人", "PROVINCE_REPORTER");
        int existingProjects =
                jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class);
        int existingProgress =
                jdbc.queryForObject("SELECT COUNT(*) FROM project_progress", Integer.class);
        int existingSources =
                jdbc.queryForObject("SELECT COUNT(*) FROM project_source_record", Integer.class);

        var preview = imports.preview(actor, "广东项目.xlsx", workbook());
        assertThat(preview.totalRows()).isEqualTo(3);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.failedRows()).isEqualTo(2);
        assertThat(preview.rows().get(1).errorsJson()).contains("重复项目");
        assertThat(preview.rows().get(2).errorsJson()).contains("项目总金额");
        try (var template =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(imports.template(actor)));
                var failed =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
                                        imports.failedRows(actor, preview.batchId())))) {
            assertThat(template.getSheet("导入数据").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("省分");
            assertThat(template.getSheet("填写说明")).isNotNull();
            assertThat(failed.getSheet("预检未通过").getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(failed.getSheet("预检未通过").getRow(1).getCell(1).getStringCellValue())
                    .contains("重复项目");
        }
        var foreignReport =
                catchThrowable(() -> imports.failedRows(UUID.randomUUID(), preview.batchId()));
        assertThat(((BusinessException) foreignReport).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class))
                .isEqualTo(existingProjects);

        // 模拟升级前已预检的计划，不含新增属性，仍按默认值完成导入。
        jdbc.update(
                "UPDATE project_import_row SET row_payload = JSON_REMOVE(row_payload,"
                    + " '$.importAttributes') WHERE batch_id = UUID_TO_BIN(?)",
                preview.batchId().toString());
        var done = imports.confirm(actor, preview.batchId());
        assertThat(done.importedRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class))
                .isEqualTo(existingProjects + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_progress", Integer.class))
                .isEqualTo(existingProgress + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_source_record", Integer.class))
                .isEqualTo(existingSources + 1);
        assertThat(done.rows().getFirst().status()).isEqualTo("IMPORTED");
        var importedDetail = projectService.findDetail(actor, done.rows().getFirst().projectId());
        assertThat(importedDetail.importanceCode()).isEqualTo("NORMAL");
        assertThat(importedDetail.supportIndustryExpert()).isNull();

        var selective = imports.preview(actor, "选择导入.xlsx", workbookWithDistinctNames());
        assertThat(selective.validRows()).isEqualTo(3);
        var invalidSelection =
                catchThrowable(() -> imports.confirm(actor, selective.batchId(), List.of(2, 2)));
        assertThat(((BusinessException) invalidSelection).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_SELECTION_INVALID);
        var missingRow =
                catchThrowable(() -> imports.confirm(actor, selective.batchId(), List.of(2, 999)));
        assertThat(((BusinessException) missingRow).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_SELECTION_INVALID);
        assertThat(imports.find(actor, selective.batchId()).status()).isEqualTo("PREVIEWED");

        for (var row : selective.rows()) {
            if (List.of(2, 4).contains(row.rowNumber())
                    && row.nameReview() != null
                    && !row.nameReview().accepted())
                imports.decideName(
                        actor,
                        selective.batchId(),
                        row.rowNumber(),
                        row.revisionNo(),
                        true,
                        "选择行属于不同建设范围",
                        row.nameReview().fingerprint());
        }
        var selected = imports.confirm(actor, selective.batchId(), List.of(2, 4));
        assertThat(selected.importedRows()).isEqualTo(2);
        assertThat(selected.skippedRows()).isEqualTo(1);
        assertThat(selected.failedRows()).isZero();
        assertThat(selected.rows().get(1).status()).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class))
                .isEqualTo(existingProjects + 3);
        assertThat(imports.findMine(actor, PageRequest.of(0, 20)).getTotalElements()).isZero();
        var foreign = catchThrowable(() -> imports.find(UUID.randomUUID(), selective.batchId()));
        assertThat(((BusinessException) foreign).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_NOT_FOUND);

        var cancellable = imports.preview(actor, "取消计划.xlsx", workbookWithDistinctNames());
        var foreignCancel =
                catchThrowable(() -> imports.cancel(UUID.randomUUID(), cancellable.batchId()));
        assertThat(((BusinessException) foreignCancel).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_NOT_FOUND);
        var cancelled = imports.cancel(actor, cancellable.batchId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class))
                .isEqualTo(existingProjects + 3);
        var cannotConfirm =
                catchThrowable(() -> imports.confirm(actor, cancelled.batchId(), List.of(2)));
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
        jdbc.update(
                "INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES"
                    + " (UUID_TO_BIN(?),?,?,true)",
                province.toString(),
                province.toString().replace("-", ""),
                provinceName);
        jdbc.update(
                "INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES"
                    + " (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(),
                province.toString(),
                city.toString().replace("-", ""),
                cityName);
        addUser(actor, province, city, "editor-" + actor, "导入修正人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-" + leader, "省分负责人", "PROVINCE_REPORTER");

        var preview =
                imports.preview(actor, "待修正.xlsx", workbookWithLocation(provinceName, cityName));
        var batchId = preview.batchId();
        var mappedFile = mappedWorkbook(provinceName, cityName);
        var inspection = imports.inspect(actor, "外部台账.xlsx", mappedFile);
        assertThat(inspection.sheets().getFirst().headerCandidates().get(1).rowNo()).isEqualTo(2);
        var mappedPreview =
                imports.preview(
                        actor,
                        "外部台账.xlsx",
                        mappedFile,
                        "外部台账",
                        2,
                        Map.of("省分", "省份", "项目名称", "商机名称"));
        assertThat(mappedPreview.validRows()).isEqualTo(1);
        assertThat(mappedPreview.rows().getFirst().rowNumber()).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sheet_name FROM project_import_batch WHERE id ="
                                    + " UUID_TO_BIN(?)",
                                String.class,
                                mappedPreview.batchId().toString()))
                .isEqualTo("外部台账");
        assertThat(preview.rows().get(2).sourceCellsJson()).contains("项目总金额（万）");
        var missingAmount = imports.reviseRow(actor, batchId, 4, 0, Map.of("项目总金额（万）", "100"));
        assertThat(missingAmount.validRows()).isEqualTo(2);
        assertThat(missingAmount.rows().get(2).revisionNo()).isEqualTo(1);
        var renamedDuplicate = imports.reviseRow(actor, batchId, 3, 0, Map.of("项目名称", "第二条项目"));
        assertThat(renamedDuplicate.validRows()).isEqualTo(3);
        assertThat(renamedDuplicate.failedRows()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM project_import_row_revision", Integer.class))
                .isGreaterThanOrEqualTo(2);

        var stale =
                catchThrowable(
                        () -> imports.reviseRow(actor, batchId, 4, 0, Map.of("项目名称", "旧版本")));
        assertThat(((BusinessException) stale).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_ROW_VERSION_CONFLICT);
        var unknown =
                catchThrowable(() -> imports.reviseRow(actor, batchId, 4, 1, Map.of("非模板字段", "值")));
        assertThat(((BusinessException) unknown).errorCode())
                .isEqualTo(ProjectErrorCode.IMPORT_ROW_INVALID);
        var foreign =
                catchThrowable(
                        () ->
                                imports.reviseRow(
                                        UUID.randomUUID(), batchId, 4, 1, Map.of("项目名称", "越权")));
        assertThat(foreign).isInstanceOf(BusinessException.class);

        var done = imports.confirm(actor, batchId);
        assertThat(done.importedRows()).isEqualTo(3);
        var afterConfirmation =
                catchThrowable(
                        () -> imports.reviseRow(actor, batchId, 4, 1, Map.of("项目名称", "确认后修改")));
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
        jdbc.update(
                "INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES"
                    + " (UUID_TO_BIN(?),?,?,true)",
                province.toString(),
                province.toString().replace("-", ""),
                provinceName);
        jdbc.update(
                "INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES"
                    + " (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(),
                province.toString(),
                city.toString().replace("-", ""),
                cityName);
        addUser(actor, province, city, "recover-" + actor, "导入恢复人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-" + leader, "省分负责人", "PROVINCE_REPORTER");
        var preview =
                imports.preview(
                        actor, "待恢复.xlsx", workbookWithSigningSubject(provinceName, cityName));
        jdbc.update(
                "UPDATE project_import_batch SET status = 'CONFIRMING', confirmed_at ="
                    + " DATE_SUB(NOW(6), INTERVAL 16 MINUTE) WHERE id = UUID_TO_BIN(?)",
                preview.batchId().toString());

        imports.recoverStale();
        var recovered = imports.find(actor, preview.batchId());
        assertThat(recovered.status()).isEqualTo("COMPLETED");
        assertThat(recovered.importedRows()).isEqualTo(1);
        assertThat(recovered.failedRows()).isEqualTo(2);
        assertThat(recovered.rows().getFirst().status()).isEqualTo("IMPORTED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT signing_subject_code FROM project_project WHERE id ="
                                    + " UUID_TO_BIN(?)",
                                String.class,
                                recovered.rows().getFirst().projectId().toString()))
                .isEqualTo("DS_SIGN");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM project_support_expert WHERE project_id ="
                                    + " UUID_TO_BIN(?)",
                                Integer.class,
                                recovered.rows().getFirst().projectId().toString()))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT planned_bid_month FROM project_project WHERE id ="
                                            + " UUID_TO_BIN(?)",
                                        java.sql.Date.class,
                                        recovered.rows().getFirst().projectId().toString())
                                .toLocalDate())
                .isEqualTo(java.time.LocalDate.of(2026, 11, 1));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM project_application_scenario WHERE project_id"
                                    + " = UUID_TO_BIN(?)",
                                Integer.class,
                                recovered.rows().getFirst().projectId().toString()))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT construction_content_raw FROM project_project WHERE id ="
                                    + " UUID_TO_BIN(?)",
                                String.class,
                                recovered.rows().getFirst().projectId().toString()))
                .isEqualTo("客户需要建设平台");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT product_promotion_content FROM project_project WHERE id ="
                                    + " UUID_TO_BIN(?)",
                                String.class,
                                recovered.rows().getFirst().projectId().toString()))
                .isEqualTo("平台推广方案");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT remark FROM project_project WHERE id = UUID_TO_BIN(?)",
                                String.class,
                                recovered.rows().getFirst().projectId().toString()))
                .isEqualTo("导入备注");
        var detail = projectService.findDetail(actor, recovered.rows().getFirst().projectId());
        assertThat(detail.importanceCode()).isEqualTo("KEY");
        assertThat(detail.supportIndustryExpert()).isEqualTo("行业专家");
        assertThat(detail.productSupportExpertNames()).containsExactly("张专家", "李专家");
        imports.recoverStale();
        assertThat(imports.find(actor, preview.batchId()).importedRows()).isEqualTo(1);
    }

    private void addUser(
            UUID id, UUID province, UUID city, String login, String name, String role) {
        jdbc.update(
                """
INSERT INTO identity_user_account(id,login_name,display_name,province_id,city_id,enabled)
VALUES (UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),?,true)
""",
                id.toString(),
                login,
                name,
                province.toString(),
                city == null ? null : uuidBytes(city));
        jdbc.update(
                "INSERT INTO identity_user_role(user_id,role_code) VALUES (UUID_TO_BIN(?),?)",
                id.toString(),
                role);
    }

    private byte[] uuidBytes(UUID value) {
        var buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private byte[] workbook() throws Exception {
        var headers =
                List.of(
                        "省分",
                        "地市",
                        "客户名称",
                        "客户所属行业",
                        "项目名称",
                        "省分负责人",
                        "地市客户经理",
                        "项目建设内容",
                        "三化类别",
                        "项目阶段",
                        "项目总金额（万）",
                        "网络化预计金额（万）",
                        "格物预计金额（万）",
                        "大数据预计金额（万）",
                        "万悟预计金额（万）",
                        "项目进展（2026年10月10日）");
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入数据");
            for (int rowIndex = 0; rowIndex <= 3; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                var values =
                        rowIndex == 0
                                ? headers
                                : List.of(
                                        "广东省",
                                        "广州市",
                                        "某客户",
                                        "计算机、通信和其他电子设备制造业",
                                        rowIndex == 3 ? "缺金额项目" : "首条项目",
                                        "省分负责人",
                                        "张经理",
                                        "客户需要建设平台",
                                        "数字化（格物）",
                                        "方案交流",
                                        rowIndex == 3 ? "" : "100",
                                        "",
                                        "50",
                                        "",
                                        "",
                                        "完成交流");
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
        try (var source =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
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

    private byte[] workbookWithSigningSubject(String provinceName, String cityName)
            throws Exception {
        try (var source =
                        new XSSFWorkbook(
                                new java.io.ByteArrayInputStream(
                                        workbookWithLocation(provinceName, cityName)));
                var output = new ByteArrayOutputStream()) {
            var sheet = source.getSheet("导入数据");
            sheet.getRow(0).createCell(16).setCellValue("签约主体");
            sheet.getRow(1).createCell(16).setCellValue("数科签约");
            sheet.getRow(0).createCell(17).setCellValue("产品单位支撑专家");
            sheet.getRow(1).createCell(17).setCellValue("张专家，李专家");
            sheet.getRow(0).createCell(18).setCellValue("拟招标日期");
            sheet.getRow(1).createCell(18).setCellValue("2026-11-20");
            sheet.getRow(0).createCell(19).setCellValue("应用场景");
            sheet.getRow(1).createCell(19).setCellValue("柔性产线快速换产，质量精准追溯");
            sheet.getRow(0).createCell(20).setCellValue("产品推广内容");
            sheet.getRow(1).createCell(20).setCellValue("平台推广方案");
            sheet.getRow(0).createCell(21).setCellValue("备注");
            sheet.getRow(1).createCell(21).setCellValue("导入备注");
            sheet.getRow(0).createCell(22).setCellValue("重要度");
            sheet.getRow(1).createCell(22).setCellValue(" KEY ");
            sheet.getRow(0).createCell(23).setCellValue("支撑行业专家");
            sheet.getRow(1).createCell(23).setCellValue("行业专家");
            source.write(output);
            return output.toByteArray();
        }
    }
}
