package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.production.controller.ProductionPlanController;
import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.production.mapper.ProductionPlanMapper;
import com.pluto.ndi.production.service.*;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.BusinessException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class ProductionPlanIT {
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

    @Autowired ProductionPlanService plans;
    @Autowired ProductionPlanController controller;
    @Autowired ProductionImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired ImportWorkbookDirectory files;
    @Autowired com.pluto.ndi.backup.mapper.MysqlBackupMapper backup;
    UUID province, city, actor, admin, reader;

    @BeforeEach
    void setup() {
        province =
                UUID.fromString(
                        jdbc.queryForObject(
                                "SELECT BIN_TO_UUID(id) FROM organization_province WHERE"
                                        + " province_code='440000'",
                                String.class));
        city =
                UUID.fromString(
                        jdbc.queryForObject(
                                "SELECT BIN_TO_UUID(id) FROM organization_city WHERE"
                                        + " city_code='440100'",
                                String.class));
        actor = user("CITY_REPORTER", province, city);
        admin = user("SYSTEM_ADMIN", null, null);
        reader = user("GROUP_REVIEWER", null, null);
    }

    UUID user(String role, UUID p, UUID c) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " identity_user_account(id,login_name,display_name,province_id,city_id,enabled)"
                    + " VALUES(UUID_TO_BIN(?),?,'排产测试',UUID_TO_BIN(?),UUID_TO_BIN(?),TRUE)",
                id.toString(),
                "production-" + id,
                p == null ? null : p.toString(),
                c == null ? null : c.toString());
        jdbc.update("INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),?)", id.toString(), role);
        return id;
    }

    ProductionPlan.Command command(String name, String month, BigDecimal amount) {
        return new ProductionPlan.Command(
                "文本接口人",
                month,
                province,
                city,
                name,
                "测试客户",
                List.of("NETWORK", "GEWU"),
                amount,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void enforcesAmountsScopeUniquenessAndVersionedLogicalDelete() {
        String name = "排产" + UUID.randomUUID();
        var plan = plans.create(actor, command(name, "2026-10", BigDecimal.ONE));
        assertThat(plan.networkAmount()).isEqualByComparingTo("1");
        assertThat(plan.gewuAmount()).isNull();
        assertThatThrownBy(
                        () ->
                                plans.create(
                                        actor,
                                        command("  " + name + "  ", "2026-10", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        assertThat(plans.create(admin, command(name, "2026-11", BigDecimal.ONE)).id()).isNotNull();
        assertThatThrownBy(() -> plans.create(actor, command(name, "2026-12", BigDecimal.ZERO)))
                .hasMessageContaining("大于 0");
        assertThatThrownBy(() -> plans.create(actor, command(name, "2026-12", null)))
                .hasMessageContaining("至少填写一项");
        assertThatThrownBy(
                        () -> plans.create(actor, command(name, "2026-12", new BigDecimal("-1"))))
                .hasMessageContaining("非负数");
        assertThatThrownBy(
                        () ->
                                plans.create(
                                        actor, command(name, "2026-12", new BigDecimal("0.00001"))))
                .hasMessageContaining("四位小数");
        assertThat(plans.create(reader, command(name, "2026-12", BigDecimal.ONE)).id()).isNotNull();
        var changed =
                plans.update(
                        actor,
                        plan.id(),
                        plan.version(),
                        command(name, "2026-10", new BigDecimal("2.30")));
        assertThat(changed.version()).isEqualTo(1);
        assertThatThrownBy(() -> plans.delete(actor, plan.id(), 0))
                .isInstanceOf(BusinessException.class);
        var other =
                user(
                        "CITY_REPORTER",
                        province,
                        UUID.fromString(
                                jdbc.queryForObject(
                                        "SELECT BIN_TO_UUID(id) FROM organization_city WHERE"
                                                + " city_code='440300'",
                                        String.class)));
        assertThat(
                        plans.list(
                                        other,
                                        new ProductionPlanMapper.Filter(
                                                null, null, null, null, null, 0, 20))
                                .items())
                .isEmpty();
        assertThatThrownBy(() -> plans.delete(other, plan.id(), 1))
                .isInstanceOf(BusinessException.class);
        plans.delete(actor, plan.id(), 1);
        assertThat(plans.create(actor, command(name, "2026-10", BigDecimal.ONE)).id())
                .isNotEqualTo(plan.id());
        assertThat(
                        plans.list(
                                        reader,
                                        new ProductionPlanMapper.Filter(
                                                "2026-11", null, null, name, null, 0, 20))
                                .items())
                .hasSize(1)
                .allMatch(p -> p.writable());
    }

    @Test
    void sumsSplitAmountsExactlyAndRevalidatesEveryProduct() {
        String name = "分项验收" + UUID.randomUUID();
        var initial = new ProductionPlan.Command("接口", "2026-10", province, city,
                name, "客户", List.of("NETWORK", "GEWU", "BIG_DATA", "WANGWU"),
                BigDecimal.ZERO, new BigDecimal("20"), new BigDecimal("8"),
                null, new BigDecimal("0.01"), new BigDecimal("0.02"), null, null, "含备注", "产品介绍");
        var created = plans.create(actor, initial);
        assertThat(created.networkAmount()).isEqualByComparingTo("0");
        assertThat(created.productIntroduction()).isEqualTo("产品介绍");
        assertThat(created.remark()).isEqualTo("含备注");
        assertThat(created.categories()).containsExactly("GEWU", "BIG_DATA", "WANGWU");
        assertThat(created.gewuAmount()).isEqualByComparingTo("28");
        assertThat(created.bigDataAmount()).isEqualByComparingTo("0.01");
        assertThat(created.wangwuAmount()).isEqualByComparingTo("0.02");
        var edit = new ProductionPlan.Command("接口", "2026-10", province, city,
                name, "客户", List.of("GEWU"), null, null, new BigDecimal("8.5501"),
                null, null, null, null, null, "  ", "  ");
        var updated = plans.update(actor, created.id(), created.version(), edit);
        assertThat(updated.gewuAmount()).isEqualByComparingTo("8.5501");
        assertThat(updated.gewuOrderAmount()).isNull();
        assertThat(updated.remark()).isNull();
        assertThat(updated.categories()).containsExactly("GEWU");
        assertThat(updated.networkAmount()).isNull();
        for (var invalid : List.of(new BigDecimal("-1"), new BigDecimal("0.00001"))) {
            var badRevenue = new ProductionPlan.Command("接口", "2026-10", province, city,
                    name, "客户", List.of("WANGWU"), null, null, null, null, null,
                    null, invalid, null, null, null);
            assertThatThrownBy(() -> plans.validate(actor, badRevenue)).hasMessageContaining("四位小数");
        }
        var legacyCategory = new ProductionPlan.Command("接口", "2026-10", province, city,
                name, "客户", List.of("NETWORK"), null, null, BigDecimal.ONE, null, null,
                null, null, null, null, null);
        assertThat(plans.validate(actor, legacyCategory).categories()).containsExactly("GEWU");
    }

    @Test
    void downloadTemplateContainsExactlyTheSevenAmountColumnsInOrder() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new ActorPrincipal(actor), null, List.of());
        var response = controller.template(auth);
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getBody()))) {
            var header = workbook.getSheetAt(0).getRow(0);
            var amountHeaders = new ArrayList<String>();
            int firstAmount = ProductionImport.REQUIRED.size() + 1; // 客户名称为选填，仍在金额列前。
            for (int column = firstAmount; column < header.getLastCellNum() - 3; column++)
                amountHeaders.add(header.getCell(column).getStringCellValue());
            assertThat(amountHeaders).containsExactly(
                    "工业专网产品收入排产",
                    "格物工业互联网平台-政企运营平台预计下单金额",
                    "格物工业互联网平台-数智业务管理系统预计计收金额",
                    "工业大数据平台-政企运营平台预计下单金额",
                    "工业大数据平台-数智业务管理系统预计计收金额",
                    "万悟工业智能体平台-政企运营平台预计下单金额",
                    "万悟工业智能体平台-数智业务管理系统预计计收金额");
            assertThat(header.getCell(header.getLastCellNum() - 2).getStringCellValue()).isEqualTo("产品简介");
            assertThat(header.getCell(header.getLastCellNum() - 1).getStringCellValue()).isEqualTo("备注");
        }
    }

    @Test
    void previewValidatesRawExcelPrecisionAndOnlyMappedAmountsAreEditable() throws Exception {
        String name = "原始精度" + UUID.randomUUID();
        byte[] bytes;
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(workbook(name)));
                var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var numeric = sheet.getRow(1).getCell(ProductionImport.templateFields().indexOf(ProductionImport.fieldLabel(ProductionImport.AMOUNTS.getFirst())));
            numeric.setCellValue(0.00001);
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("0.00"));
            numeric.setCellStyle(style);
            book.write(out);
            bytes = out.toByteArray();
        }
        var uploaded = imports.upload(actor, "排产.xlsx", bytes, null, null);
        var mapping = new java.util.HashMap<String, String>();
        ProductionImport.REQUIRED.forEach(field -> mapping.put(field, field));
        var order = ProductionImport.AMOUNTS.getFirst();
        mapping.put(order, ProductionImport.fieldLabel(order));
        var configuration = new Configuration("排产数据", 1, mapping);
        var mapped = imports.save(actor, uploaded.id(), uploaded.version(), 2, configuration, null);
        var preview = imports.save(actor, mapped.id(), mapped.version(), 3, configuration, null);
        assertThat(preview.batch().rows().getFirst().errorsJson()).contains("最多四位小数");
        var batch = preview.batch().batchId();
        try (var failed = new XSSFWorkbook(new java.io.ByteArrayInputStream(imports.originalFailedRows(actor, batch)))) {
            var sheet = failed.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(preview.batch().failedRows() + 1);
            var reasonColumn = sheet.getRow(0).getLastCellNum() - 1;
            assertThat(sheet.getRow(0).getCell(reasonColumn).getStringCellValue()).isEqualTo("失败原因");
            assertThat(sheet.getRow(1).getCell(reasonColumn).getStringCellValue()).contains("最多四位小数");
        }
        assertThatThrownBy(() -> imports.revise(actor, batch, 2, 0,
                Map.of("格物工业互联网平台-数智业务管理系统预计计收金额", "9")))
                .hasMessageContaining("修正字段无效");
        var corrected = imports.revise(actor, batch, 2, 0, Map.of(order, "1"));
        assertThat(corrected.rows().getFirst().status()).isEqualTo("READY");
        assertThat(imports.confirm(actor, batch, List.of(2)).importedRows()).isEqualTo(1);
    }

    byte[] workbook(String name) throws Exception {
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("排产数据");
            var header = sheet.createRow(0);
            var fields = ProductionImport.templateFields();
            for (int i = 0; i < fields.size(); i++)
                header.createCell(i).setCellValue(fields.get(i));
            for (int i = 0; i < 3; i++) {
                var row = sheet.createRow(i + 1);
                String[] values = {
                    "文本接口人",
                    "2026-10",
                    "广东省",
                    "广州市",
                    i == 2 ? name + "需修正" : name,
                    "测试客户",
                    i == 2 ? "" : "1",
                    "", "", "", "", "", "",
                    "推进中", ""
                };
                for (int j = 0; j < values.length; j++) row.createCell(j).setCellValue(values[j]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void wizardBlocksMissingMappingsRecoversEditsChecksDuplicatesAndCompletesOnce()
            throws Exception {
        String name = "导入" + UUID.randomUUID();
        var uploaded = imports.upload(actor, "排产.xlsx", workbook(name), null, null);
        try (var connection =
                java.sql.DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertThat(backup.references(connection))
                    .anySatisfy(
                            ref -> {
                                assertThat(ref.key()).startsWith("production-imports/");
                                assertThat(ref.sha256()).hasSize(64);
                            });
        }
        assertThatThrownBy(
                        () ->
                                imports.save(
                                        actor, uploaded.id(), 0, 3, uploaded.configuration(), null))
                .isInstanceOf(BusinessException.class);
        var incomplete = new Configuration("排产数据", 1, Map.of("项目名称", "项目名称"));
        var mapped = imports.save(actor, uploaded.id(), 0, 2, incomplete, null);
        assertThatThrownBy(
                        () ->
                                imports.save(
                                        actor, mapped.id(), mapped.version(), 3, incomplete, null))
                .hasMessageContaining("请先映射必填字段");
        var full =
                new Configuration(
                        "排产数据",
                        1,
                        ProductionImport.fields().stream()
                                .collect(java.util.stream.Collectors.toMap(f -> f, ProductionImport::fieldLabel)));
        var preview = imports.save(actor, mapped.id(), mapped.version(), 3, full, null);
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.batch().failedRows()).isEqualTo(2);
        assertThat(imports.current(actor).step()).isEqualTo(3);
        assertThatThrownBy(() -> imports.batch(admin, preview.batch().batchId()))
                .isInstanceOf(BusinessException.class);
        var corrected =
                imports.revise(
                        actor, preview.batch().batchId(), 4, 0, Map.of("工业专网排产（万元）", "1.25"));
        assertThat(corrected.validRows()).isEqualTo(2);
        var done = imports.confirm(actor, corrected.batchId(), List.of(2, 4));
        assertThat(done.importedRows()).isEqualTo(2);
        assertThat(done.skippedRows()).isEqualTo(1);
        assertThat(imports.current(actor)).isNull();
        assertThatThrownBy(() -> imports.confirm(actor, corrected.batchId(), List.of(3)))
                .isInstanceOf(BusinessException.class);
        var again = imports.upload(actor, "排产.xlsx", workbook(name), null, null);
        assertThat(again.step()).isEqualTo(1);
        imports.discard(actor, again.id(), again.version());
        assertThat(imports.current(actor)).isNull();
    }
}
