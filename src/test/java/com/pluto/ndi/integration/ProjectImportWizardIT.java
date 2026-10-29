package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.domain.ProjectImportTask.Configuration;
import com.pluto.ndi.projects.service.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class ProjectImportWizardIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>("netiedge/minio:RELEASE.2025-01-20T14-49-07Z")
                    .withEnv("MINIO_ROOT_USER", "ndi_test_admin")
                    .withEnv("MINIO_ROOT_PASSWORD", "ndi_test_password")
                    .withCommand("server /data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add(
                "ndi.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add(
                "ndi.storage.public-endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add("ndi.storage.access-key", () -> "ndi_test_admin");
        r.add("ndi.storage.secret-key", () -> "ndi_test_password");
        r.add("ndi.storage.auto-create-bucket", () -> "true");
        r.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }

    @Autowired ProjectImportWizardService wizard;
    @Autowired ProjectImportService imports;
    @Autowired ProjectService projects;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired ImportWorkbookDirectory files;
    @Autowired com.pluto.ndi.backup.mapper.MysqlBackupMapper backup;

    private record Fixture(UUID actor, UUID province, UUID city) {}

    private Fixture fixture() {
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
        jdbc.update(
                "INSERT INTO"
                    + " identity_user_account(id,login_name,display_name,province_id,city_id,enabled)"
                    + " VALUES(UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),TRUE)",
                actor.toString(),
                "import-" + actor,
                "导入人员",
                province.toString(),
                city.toString());
        jdbc.update(
                "INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),'CITY_REPORTER')",
                actor.toString());
        return new Fixture(actor, province, city);
    }

    private byte[] workbook(String... names) throws Exception {
        try (var book = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            book.createSheet("说明").createRow(0).createCell(0).setCellValue("请使用数据工作表");
            var sheet = book.createSheet("导入数据");
            sheet.createRow(0).createCell(0).setCellValue("项目导入");
            String[] headers = {
                "省分",
                "地市",
                "客户名称",
                "客户所属行业",
                "项目名称",
                "项目建设内容",
                "三化类别",
                "项目阶段",
                "项目总金额（万）",
                "网络化预计金额（万）",
                "格物预计金额（万）",
                "大数据预计金额（万）",
                "万悟预计金额（万）",
                "项目负责人",
                "首条项目进展",
                "下一步动作"
            };
            var header = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int row = 0; row < names.length; row++) {
                String[] values = {
                    "广东省",
                    "广州市",
                    "客户",
                    "石油、煤炭及其他燃料加工业",
                    names[row],
                    "建设内容",
                    "网络化",
                    "方案交流",
                    "100",
                    "50",
                    "",
                    "",
                    "",
                    "外部负责人甲；外部负责人乙",
                    "已完成首次交流",
                    "安排下次沟通"
                };
                var r = sheet.createRow(row + 2);
                for (int c = 0; c < values.length; c++) r.createCell(c).setCellValue(values[c]);
            }
            book.write(out);
            return out.toByteArray();
        }
    }

    private Configuration configuration(ProjectImportWizardService.View task) {
        return new Configuration(
                "导入数据",
                2,
                task.inspection().sheets().stream()
                        .filter(sheet -> sheet.name().equals("导入数据"))
                        .findFirst()
                        .orElseThrow()
                        .headerCandidates()
                        .stream()
                        .filter(header -> header.rowNo() == 2)
                        .findFirst()
                        .orElseThrow()
                        .headers()
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(field -> field, field -> field)));
    }

    private ProjectImportWizardService.View preview(Fixture f, String... names) throws Exception {
        var upload = wizard.upload(f.actor(), null, null, "数据.xlsx", workbook(names));
        var mapped =
                wizard.save(
                        f.actor(), upload.id(), upload.version(), 2, configuration(upload), null);
        return wizard.save(
                f.actor(), mapped.id(), mapped.version(), 3, mapped.configuration(), null);
    }

    private ProjectSummary create(Fixture f, String name) {
        return projects.createProject(
                f.actor(),
                new CreateProjectCommand(
                        f.province(),
                        f.city(),
                        name,
                        "客户",
                        "C25",
                        "需求",
                        BigDecimal.TEN,
                        null,
                        null,
                        List.of(new CreateProjectCommand.CategoryAmount("NETWORK", BigDecimal.ONE)),
                        "首次进展",
                        "跟进",
                        null,
                        true,
                        "测试独立项目",
                        "PROVINCE_SIGN",
                        List.of(),
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        List.of("负责人")));
    }

    @Test
    void persistsAdjacentStepsVersionsAndCompletesOnlyOnceWithSelectedRows() throws Exception {
        var f = fixture();
        var bytes = workbook("油库检测" + UUID.randomUUID(), "气象观测" + UUID.randomUUID());
        var uploaded = wizard.upload(f.actor(), null, null, "数据.xlsx", bytes);
        var location =
                jdbc.queryForMap(
                        "SELECT bucket_name,object_key FROM project_import_task WHERE"
                                + " actor_id=UUID_TO_BIN(?)",
                        f.actor().toString());
        try (var connection =
                java.sql.DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertThat(backup.references(connection))
                    .anySatisfy(
                            ref -> {
                                assertThat(ref.key()).isEqualTo(location.get("object_key"));
                                assertThat(ref.size()).isEqualTo(bytes.length);
                                assertThat(ref.sha256()).hasSize(64);
                            });
        }
        assertThat(
                        files.read(
                                (String) location.get("bucket_name"),
                                (String) location.get("object_key")))
                .isEqualTo(bytes);
        assertThat(wizard.current(f.actor()).id()).isEqualTo(uploaded.id());
        assertThatThrownBy(
                        () ->
                                wizard.save(
                                        f.actor(),
                                        uploaded.id(),
                                        uploaded.version(),
                                        3,
                                        configuration(uploaded),
                                        null))
                .isInstanceOf(BusinessException.class);
        var mapped =
                wizard.save(
                        f.actor(),
                        uploaded.id(),
                        uploaded.version(),
                        2,
                        configuration(uploaded),
                        null);
        assertThatThrownBy(
                        () ->
                                wizard.save(
                                        f.actor(),
                                        uploaded.id(),
                                        uploaded.version(),
                                        2,
                                        configuration(uploaded),
                                        null))
                .isInstanceOf(BusinessException.class);
        var checked =
                wizard.save(
                        f.actor(), mapped.id(), mapped.version(), 3, mapped.configuration(), null);
        assertThat(checked.batch().validRows()).isEqualTo(2);
        assertThatThrownBy(
                        () ->
                                wizard.save(
                                        f.actor(),
                                        checked.id(),
                                        checked.version(),
                                        1,
                                        checked.configuration(),
                                        null))
                .isInstanceOf(BusinessException.class);
        var back =
                wizard.save(
                        f.actor(),
                        checked.id(),
                        checked.version(),
                        2,
                        checked.configuration(),
                        null);
        var again =
                wizard.save(f.actor(), back.id(), back.version(), 3, back.configuration(), null);
        assertThat(again.batch().batchId()).isEqualTo(checked.batch().batchId());
        var done = imports.confirm(f.actor(), again.batch().batchId(), List.of(3));
        assertThat(done.importedRows()).isEqualTo(1);
        assertThat(done.skippedRows()).isEqualTo(1);
        assertThat(wizard.current(f.actor())).isNull();
        assertThat(
                        imports.findMine(
                                f.actor(), org.springframework.data.domain.PageRequest.of(0, 20)))
                .isEmpty();
        assertThatThrownBy(() -> imports.confirm(f.actor(), done.batchId()))
                .isInstanceOf(BusinessException.class);
        var next = wizard.upload(f.actor(), null, null, "新数据.xlsx", bytes);
        assertThat(next.id()).isNotEqualTo(uploaded.id());
        assertThat(next.step()).isEqualTo(1);
        wizard.discard(f.actor(), next.id(), next.version());
        assertThat(wizard.current(f.actor())).isNull();
    }

    @Test
    void requiresTieredAcknowledgementAndInvalidatesConfirmationOnNameChange() throws Exception {
        var f = fixture();
        String prefix = "橡胶车间" + UUID.randomUUID();
        create(f, prefix + "温度传感器采集一期项目");
        var view = preview(f, prefix + "温度传感器采集二期项目");
        var batch = view.batch();
        var row = batch.rows().getFirst();
        assertThat(row.nameReview().level()).isEqualTo("STRONG");
        assertThatThrownBy(() -> imports.confirm(f.actor(), batch.batchId()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                imports.decideName(
                                        f.actor(),
                                        batch.batchId(),
                                        row.rowNumber(),
                                        row.revisionNo(),
                                        true,
                                        "短",
                                        row.nameReview().fingerprint()))
                .isInstanceOf(BusinessException.class);
        var accepted =
                imports.decideName(
                        f.actor(),
                        batch.batchId(),
                        row.rowNumber(),
                        row.revisionNo(),
                        true,
                        "这是不同车间的二期建设",
                        row.nameReview().fingerprint());
        assertThat(accepted.rows().getFirst().nameReview().accepted()).isTrue();
        var edited =
                imports.reviseRow(
                        f.actor(),
                        batch.batchId(),
                        row.rowNumber(),
                        accepted.rows().getFirst().revisionNo(),
                        Map.of("项目名称", prefix + "温度传感器采集三期项目"));
        var changed = edited.rows().getFirst();
        assertThat(changed.nameReview().confirmed()).isFalse();
        assertThatThrownBy(
                        () ->
                                imports.decideName(
                                        f.actor(),
                                        batch.batchId(),
                                        changed.rowNumber(),
                                        changed.revisionNo(),
                                        true,
                                        "旧候选不能直接套用",
                                        row.nameReview().fingerprint()))
                .isInstanceOf(BusinessException.class);
        var approved =
                imports.decideName(
                        f.actor(),
                        batch.batchId(),
                        changed.rowNumber(),
                        changed.revisionNo(),
                        true,
                        "实际为三期独立建设内容",
                        changed.nameReview().fingerprint());
        assertThat(imports.confirm(f.actor(), batch.batchId()).importedRows()).isEqualTo(1);
        assertThat(approved.rows().getFirst().sourceCellsJson()).contains("实际为三期独立建设内容");
    }

    @Test
    void weakMatchesNeedConfirmationAndConcurrentNewNamesCannotBypassPrecheck() throws Exception {
        var f = fixture();
        create(f, "hello");
        var checked = preview(f, "hallo");
        var row = checked.batch().rows().getFirst();
        assertThat(row.nameReview().level()).isEqualTo("WEAK");
        imports.decideName(
                f.actor(),
                checked.batch().batchId(),
                row.rowNumber(),
                row.revisionNo(),
                true,
                "",
                row.nameReview().fingerprint());
        assertThat(imports.confirm(f.actor(), checked.batch().batchId()).importedRows())
                .isEqualTo(1);
        String name = "无人机巡检" + UUID.randomUUID();
        var next = preview(f, name);
        create(f, name);
        var result = imports.confirm(f.actor(), next.batch().batchId());
        assertThat(result.importedRows()).isZero();
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(wizard.current(f.actor())).isNull();
    }

    @Test
    void releasesLaterDuplicatesAfterEarlierNameCorrectionAndAllowsExplainedDatabaseNames()
            throws Exception {
        var f = fixture();
        var invalid = preview(f, "——", "x".repeat(201));
        assertThat(invalid.batch().validRows()).isZero();
        assertThat(invalid.batch().rows())
                .allMatch(row -> row.errorsJson().contains("项目名称须为2至200字符"));
        wizard.discard(f.actor(), invalid.id(), invalid.version());
        String name = "泵站控制" + UUID.randomUUID();
        var checked = preview(f, name, name);
        assertThat(checked.batch().validRows()).isEqualTo(1);
        var revised =
                imports.reviseRow(
                        f.actor(),
                        checked.batch().batchId(),
                        3,
                        0,
                        Map.of("项目名称", "城市交通" + UUID.randomUUID()));
        assertThat(revised.validRows()).isEqualTo(2);
        assertThat(imports.confirm(f.actor(), checked.batch().batchId()).importedRows())
                .isEqualTo(2);
        var same = preview(f, name);
        var row = same.batch().rows().getFirst();
        assertThat(row.status()).isEqualTo("READY");
        assertThat(row.nameReview().score()).isEqualTo(1);
        imports.decideName(
                f.actor(),
                same.batch().batchId(),
                row.rowNumber(),
                row.revisionNo(),
                true,
                "同名的不同客户建设项目",
                row.nameReview().fingerprint());
        assertThat(imports.confirm(f.actor(), same.batch().batchId()).importedRows()).isEqualTo(1);
    }

    @Test
    void serializesFirstUploadAndIsolatesActorTasks() throws Exception {
        var f = fixture();
        var other = fixture();
        var bytes = workbook("独立工作表" + UUID.randomUUID());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                    executor.submit(
                            () -> {
                                try {
                                    return wizard.upload(f.actor(), null, null, "并行.xlsx", bytes)
                                            .id();
                                } catch (BusinessException e) {
                                    return null;
                                }
                            });
            var second =
                    executor.submit(
                            () -> {
                                try {
                                    return wizard.upload(f.actor(), null, null, "并行.xlsx", bytes)
                                            .id();
                                } catch (BusinessException e) {
                                    return null;
                                }
                            });
            assertThat(
                            java.util.stream.Stream.of(first.get(), second.get())
                                    .filter(Objects::nonNull)
                                    .count())
                    .isEqualTo(1);
        }
        assertThat(wizard.current(other.actor())).isNull();
        assertThatThrownBy(
                        () ->
                                wizard.discard(
                                        other.actor(),
                                        wizard.current(f.actor()).id(),
                                        wizard.current(f.actor()).version()))
                .isInstanceOf(BusinessException.class);
        wizard.discard(
                f.actor(), wizard.current(f.actor()).id(), wizard.current(f.actor()).version());
    }

    @Test
    void rejectsMissingRequiredAndConditionalMappingBeforePreview() throws Exception {
        var f = fixture();
        var upload =
                wizard.upload(f.actor(), null, null, "映射.xlsx", workbook("映射" + UUID.randomUUID()));
        var fields = new HashMap<>(configuration(upload).fieldMapping());
        fields.put("客户名称", "");
        var incomplete =
                wizard.save(
                        f.actor(),
                        upload.id(),
                        upload.version(),
                        2,
                        new Configuration("导入数据", 2, fields),
                        null);
        assertThatThrownBy(
                        () ->
                                wizard.save(
                                        f.actor(),
                                        incomplete.id(),
                                        incomplete.version(),
                                        3,
                                        incomplete.configuration(),
                                        null))
                .hasMessageContaining("客户名称");
        fields.put("客户名称", "客户名称");
        for (var amount : com.pluto.ndi.projects.util.ProjectImportAmounts.fields())
            fields.put(amount, "");
        var missingAmount = new Configuration("导入数据", 2, fields);
        assertThatThrownBy(
                        () ->
                                wizard.save(
                                        f.actor(),
                                        incomplete.id(),
                                        incomplete.version(),
                                        3,
                                        missingAmount,
                                        null))
                .hasMessageContaining("至少一项三化预计金额");
        assertThat(wizard.current(f.actor()).step()).isEqualTo(2);
        assertThat(wizard.current(f.actor()).batch()).isNull();
    }

    @Test
    void optionalProgressMappingsCanBeOmittedAndDoNotCreateAnEmptyProgress() throws Exception {
        var f = fixture();
        var upload = wizard.upload(f.actor(), null, null, "选填进展.xlsx", workbook("选填进展" + UUID.randomUUID()));
        assertThat(upload.fieldRules().stream()
                .filter(rule -> List.of("首条项目进展", "下一步动作").contains(rule.name())))
                .allMatch(rule -> rule.requirement().equals("OPTIONAL"));
        var fields = new HashMap<>(configuration(upload).fieldMapping());
        fields.put("首条项目进展", "");
        fields.put("下一步动作", "");
        var mapped = wizard.save(f.actor(), upload.id(), upload.version(), 2,
                new Configuration("导入数据", 2, fields), null);
        var checked = wizard.save(f.actor(), mapped.id(), mapped.version(), 3, mapped.configuration(), null);
        assertThat(checked.batch().validRows()).isEqualTo(1);
        var done = imports.confirm(f.actor(), checked.batch().batchId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_progress WHERE project_id=UUID_TO_BIN(?)",
                Integer.class, done.rows().getFirst().projectId().toString())).isZero();
    }

    @Test
    void readsNewPreviewAfterWaitingWithAnOlderTransactionSnapshot() throws Exception {
        var f = fixture();
        var upload =
                wizard.upload(
                        f.actor(), null, null, "并发预检.xlsx", workbook("并发当前读" + UUID.randomUUID()));
        var mapped =
                wizard.save(
                        f.actor(), upload.id(), upload.version(), 2, configuration(upload), null);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var view =
                    transactions.execute(
                            status -> {
                                // 建立早于新批次的事务快照，再让另一事务完成预检。
                                jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM project_import_batch", Long.class);
                                try {
                                    executor.submit(
                                                    () ->
                                                            wizard.save(
                                                                    f.actor(),
                                                                    mapped.id(),
                                                                    mapped.version(),
                                                                    3,
                                                                    mapped.configuration(),
                                                                    null))
                                            .get(20, TimeUnit.SECONDS);
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                                return wizard.current(f.actor());
                            });
            assertThat(view.step()).isEqualTo(3);
            assertThat(view.batch().rows()).hasSize(1);
            assertThat(view.batch().validRows()).isEqualTo(1);
        }
    }
}
