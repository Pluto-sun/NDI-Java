package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.revenue.service.RevenueImportService;
import com.pluto.ndi.revenue.service.RevenueService;
import com.pluto.ndi.revenue.util.RevenueFields;
import com.pluto.ndi.shared.imports.ImportModels.Configuration;
import com.pluto.ndi.shared.web.BusinessException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;

/** 六字段唯一键在手工创建、导入和并发写入中使用相同口径。 */
@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class RevenueBusinessKeyIT {
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

    @Autowired RevenueService service;
    @Autowired RevenueImportService imports;
    @Autowired JdbcTemplate jdbc;
    UUID admin;

    @BeforeEach
    void account() {
        admin = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO identity_user_account(id,login_name,display_name,enabled)"
                        + " VALUES(UUID_TO_BIN(?),?,'唯一键验收',TRUE)",
                admin.toString(),
                "revenue-key-" + admin);
        jdbc.update(
                "INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),'SYSTEM_ADMIN')",
                admin.toString());
    }

    Map<String, String> source() {
        var result =
                new LinkedHashMap<>(
                        Map.of(
                                "月账期",
                                "202611",
                                "省份名称",
                                "--",
                                "地市/组织名称",
                                "唯一键验收组织",
                                "合同编号",
                                "CONTRACT-" + UUID.randomUUID(),
                                "条款编号",
                                "001",
                                "条款金额",
                                "100",
                                RevenueFields.SOURCE_AMOUNT,
                                "10",
                                "业务号码/项目编号",
                                "000001"));
        result.put("自然客户名称", "验收自然客户");
        return result;
    }

    Map<String, String> withBusiness(Map<String, String> source, String number) {
        var result = new LinkedHashMap<>(source);
        result.put("业务号码/项目编号", number);
        return result;
    }

    @Test
    void creationUsesAllFourFieldsAndPreservesEmptyAndFullTextValues() {
        var source = source();
        var first = service.create(admin, source);
        assertThat(service.duplicate(service.validate(admin, source))).isTrue();
        assertThatThrownBy(() -> service.create(admin, withBusiness(source, "  000001  ")))
                .isInstanceOf(BusinessException.class);
        assertThat(service.duplicate(service.validate(admin, withBusiness(source, "000002"))))
                .isFalse();
        assertThat(service.create(admin, withBusiness(source, "000002")).id())
                .isNotEqualTo(first.id());
        assertThat(service.create(admin, withBusiness(source, "1")).source())
                .containsEntry("业务号码/项目编号", "1");
        assertThat(service.create(admin, withBusiness(source, "A")).id())
                .isNotEqualTo(service.create(admin, withBusiness(source, "a")).id());
        String prefix = "长".repeat(1999);
        assertThat(service.create(admin, withBusiness(source, prefix + "甲")).id())
                .isNotEqualTo(service.create(admin, withBusiness(source, prefix + "乙")).id());
        var empty = withBusiness(source, "");
        var blank = service.create(admin, empty);
        var omitted = new LinkedHashMap<>(source);
        omitted.remove("业务号码/项目编号");
        assertThatThrownBy(() -> service.create(admin, omitted))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(admin, withBusiness(source, "   ")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(admin, withBusiness(source, "--")))
                .isInstanceOf(BusinessException.class);
        assertThat(service.detail(admin, blank.id()).source()).containsEntry("业务号码/项目编号", "");
        // 绕过服务层仍由数据库阻断，防止两个并发请求同时通过预查。
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
INSERT INTO revenue_entry(id,account_month,province_id,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
SELECT UUID_TO_BIN(?),account_month,province_id,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at FROM revenue_entry WHERE id=UUID_TO_BIN(?)
""",
                                        UUID.randomUUID().toString(),
                                        first.id().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void importChecksFourFieldsAgainstBothWorkbookAndExistingRecords() throws Exception {
        var source = source();
        service.create(admin, withBusiness(source, "EXISTING"));
        var rows =
                List.of(
                        source,
                        withBusiness(source, "000002"),
                        source,
                        withBusiness(source, "EXISTING"),
                        withBusiness(source, ""),
                        withBusiness(source, ""));
        var task = imports.upload(admin, "四字段唯一键.xlsx", workbook(rows), null, null);
        var mapping = new LinkedHashMap<String, String>();
        RevenueFields.FIELDS.forEach(field -> mapping.put(field, field));
        var config = new Configuration("来源数据", 1, mapping);
        var second = imports.save(admin, task.id(), task.version(), 2, config, null);
        var preview = imports.save(admin, task.id(), second.version(), 3, config, null);
        assertThat(preview.batch().validRows()).isEqualTo(3);
        assertThat(preview.batch().failedRows()).isEqualTo(3);
        assertThat(preview.batch().rows().get(2).errorsJson()).contains("文件内前序记录重复");
        assertThat(preview.batch().rows().get(3).errorsJson()).contains("系统内已存在");
        // 预检后发生外部写入，最终确认仍重新检查，不能重复落库。
        service.create(admin, source);
        assertThatThrownBy(
                        () -> imports.confirm(admin, preview.batch().batchId(), List.of(2, 3, 6)))
                .isInstanceOf(BusinessException.class);
        var refreshed = imports.current(admin);
        var revised =
                imports.revise(
                        admin, refreshed.batch().batchId(), 2, 0, Map.of("业务号码/项目编号", "000003"));
        assertThat(revised.validRows()).isEqualTo(3);
        var completed = imports.confirm(admin, preview.batch().batchId(), List.of(2, 3, 6));
        assertThat(completed.importedRows()).isEqualTo(3);
        assertThat(imports.current(admin)).isNull();
    }

    byte[] workbook(List<Map<String, String>> rows) throws Exception {
        try (var book = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("来源数据");
            var header = sheet.createRow(0);
            for (int col = 0; col < RevenueFields.FIELDS.size(); col++)
                header.createCell(col).setCellValue(RevenueFields.FIELDS.get(col));
            for (int index = 0; index < rows.size(); index++) {
                var row = sheet.createRow(index + 1);
                for (int col = 0; col < RevenueFields.FIELDS.size(); col++)
                    row.createCell(col)
                            .setCellValue(
                                    rows.get(index)
                                            .getOrDefault(RevenueFields.FIELDS.get(col), ""));
            }
            book.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void digitalTechIsAnIndependentSourceLabelWithFixedOrganizationAndOrganizationAccess() {
        var source = source();
        source.put("省份名称", "数科");
        source.put("地市/组织名称", "");
        source.put("审签日期", "--");
        var entry = service.create(admin, source);
        assertThat(entry.specificOrganization()).isTrue();
        assertThat(entry.provinceId()).isNull();
        assertThat(entry.source())
                .containsEntry("省份名称", "数科")
                .containsEntry("地市/组织名称", "联通数字科技有限公司本部")
                .containsEntry("审签日期", "");
        assertThat(service.reference(admin).fixedOrganizations())
                .containsEntry("数科", "联通数字科技有限公司本部");
        var digitalTech =
                service.list(
                        admin,
                        new com.pluto.ndi.revenue.mapper.RevenueMapper.Filter(
                                null, "DS_TECH", null, null, 0, 100));
        assertThat(digitalTech.items()).extracting(e -> e.id()).contains(entry.id());
        var organizations =
                service.list(
                        admin,
                        new com.pluto.ndi.revenue.mapper.RevenueMapper.Filter(
                                null, "ORGANIZATION", null, null, 0, 100));
        assertThat(organizations.items()).extracting(e -> e.id()).doesNotContain(entry.id());
        source.put("地市/组织名称", "其他组织");
        assertThatThrownBy(() -> service.create(admin, source))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅允许选择");
    }

    @Test
    void clauseNormalizationIsSharedByCreationWorkbookDuplicatesAndFinalInsertion()
            throws Exception {
        var source = source();
        source.put("条款编号", "002");
        source.put("审签日期", "2026/7/20 0:00:00");
        var saved = service.create(admin, source);
        assertThat(saved.clauseNo()).isEqualTo("2");
        assertThat(saved.source()).containsEntry("条款编号", "2").containsEntry("审签日期", "2026-07-20");
        var same = new LinkedHashMap<>(source);
        same.put("条款编号", "2");
        assertThatThrownBy(() -> service.create(admin, same)).isInstanceOf(BusinessException.class);
        var different = withBusiness(same, "OTHER");
        assertThat(service.create(admin, different).id()).isNotEqualTo(saved.id());
        var first = source();
        first.put("条款编号", "0003");
        first.put("审签日期", "2026-07-20 00:00:00");
        var duplicate = new LinkedHashMap<>(first);
        duplicate.put("条款编号", "3");
        var task =
                imports.upload(
                        admin, "数字条款.xlsx", workbook(List.of(first, duplicate, same)), null, null);
        var mapping = new LinkedHashMap<String, String>();
        RevenueFields.FIELDS.forEach(f -> mapping.put(f, f));
        var config = new Configuration("来源数据", 1, mapping);
        var second = imports.save(admin, task.id(), task.version(), 2, config, null);
        var preview = imports.save(admin, task.id(), second.version(), 3, config, null);
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.batch().failedRows()).isEqualTo(2);
        assertThat(preview.batch().rows().get(1).errorsJson()).contains("文件内前序记录重复");
        assertThat(preview.batch().rows().get(2).errorsJson()).contains("系统内已存在");
        var done = imports.confirm(admin, preview.batch().batchId(), List.of(2));
        var imported = service.detail(admin, done.rows().getFirst().recordId());
        assertThat(imported.clauseNo()).isEqualTo("3");
        assertThat(imported.source())
                .containsEntry("条款编号", "3")
                .containsEntry("审签日期", "2026-07-20");
    }

    @Test
    void concurrentIdenticalKeysHaveOneWinnerWhileDifferentBusinessNumbersBothSucceed()
            throws Exception {
        var source = source();
        assertThat(concurrent(source, source)).containsExactlyInAnyOrder(true, false);
        var other = source();
        assertThat(concurrent(other, withBusiness(other, "000002"))).containsExactly(true, true);
    }

    @Test
    void customerAndProductEachDistinguishCreationImportAndConcurrentWrites() throws Exception {
        var base = source();
        base.put("自然客户名称", "客户甲");
        base.put("产品编码", "0002");
        service.create(admin, base);
        for (var field : List.of("自然客户名称", "产品编码")) {
            var changed = new LinkedHashMap<>(base);
            changed.put(field, field.equals("产品编码") ? "2" : "客户乙");
            assertThat(service.duplicate(service.validate(admin, changed))).isFalse();
            service.create(admin, changed);
            assertThatThrownBy(() -> service.create(admin, changed))
                    .isInstanceOf(BusinessException.class);
        }
        var trimmed = new LinkedHashMap<>(base);
        trimmed.put("自然客户名称", "  客户甲  ");
        assertThatThrownBy(() -> service.create(admin, trimmed))
                .isInstanceOf(BusinessException.class);
        var first = new LinkedHashMap<>(base);
        first.put("自然客户名称", "新客户");
        var otherCustomer = new LinkedHashMap<>(first);
        otherCustomer.put("自然客户名称", "另一客户");
        var otherProduct = new LinkedHashMap<>(first);
        otherProduct.put("产品编码", "0003");
        var task =
                imports.upload(
                        admin,
                        "六字段.xlsx",
                        workbook(List.of(first, otherCustomer, otherProduct, first, base)),
                        null,
                        null);
        var mapping = new LinkedHashMap<String, String>();
        RevenueFields.FIELDS.forEach(f -> mapping.put(f, f));
        var config = new Configuration("来源数据", 1, mapping);
        var second = imports.save(admin, task.id(), task.version(), 2, config, null);
        var preview = imports.save(admin, task.id(), second.version(), 3, config, null);
        assertThat(preview.batch().validRows()).isEqualTo(3);
        assertThat(preview.batch().failedRows()).isEqualTo(2);
        assertThat(preview.batch().rows().get(3).errorsJson()).contains("文件内前序记录重复");
        assertThat(preview.batch().rows().get(4).errorsJson()).contains("系统内已存在");
        assertThat(
                        imports.confirm(admin, preview.batch().batchId(), List.of(2, 3, 4))
                                .importedRows())
                .isEqualTo(3);
        var concurrentBase = source();
        var differentCustomer = new LinkedHashMap<>(concurrentBase);
        differentCustomer.put("自然客户名称", "并发客户");
        assertThat(concurrent(concurrentBase, differentCustomer)).containsExactly(true, true);
        var another = source();
        var differentProduct = new LinkedHashMap<>(another);
        differentProduct.put("产品编码", "0002");
        assertThat(concurrent(another, differentProduct)).containsExactly(true, true);
    }

    @Test
    void allFiveOptionalIdentifiersShareEmptyPlaceholderAndMissingIdentity() throws Exception {
        var minimal =
                new LinkedHashMap<>(
                        Map.of("月账期", "202612", "条款金额", "100", RevenueFields.SOURCE_AMOUNT, "0"));
        var saved = service.create(admin, minimal);
        var placeholders = new LinkedHashMap<>(minimal);
        for (var field : List.of("合同编号", "条款编号", "业务号码/项目编号", "自然客户名称", "产品编码"))
            placeholders.put(field, " -- ");
        assertThat(service.duplicate(service.validate(admin, placeholders))).isTrue();
        assertThatThrownBy(() -> service.create(admin, placeholders))
                .isInstanceOf(BusinessException.class);
        for (var field : List.of("合同编号", "条款编号", "业务号码/项目编号", "自然客户名称", "产品编码")) {
            var changed = new LinkedHashMap<>(minimal);
            changed.put(field, "001");
            assertThat(service.create(admin, changed).id()).isNotEqualTo(saved.id());
        }
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
INSERT INTO revenue_entry(id,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
SELECT UUID_TO_BIN(?),account_month,FALSE,'--','--',clause_amount,source_amount,JSON_OBJECT('业务号码/项目编号','--','自然客户名称','--','产品编码','--'),created_by,created_at,updated_at FROM revenue_entry WHERE id=UUID_TO_BIN(?)
""",
                                        UUID.randomUUID().toString(),
                                        saved.id().toString()))
                .isInstanceOf(DataIntegrityViolationException.class);
        var first = new LinkedHashMap<>(minimal);
        first.put("月账期", "202701");
        placeholders.put("月账期", "202701");
        var named = new LinkedHashMap<>(first);
        named.put("自然客户名称", "另一客户");
        var task =
                imports.upload(
                        admin,
                        "空标识.xlsx",
                        workbook(List.of(first, placeholders, named)),
                        null,
                        null);
        var mapping = new LinkedHashMap<String, String>();
        RevenueFields.FIELDS.forEach(f -> mapping.put(f, f));
        var config = new Configuration("来源数据", 1, mapping);
        var second = imports.save(admin, task.id(), task.version(), 2, config, null);
        var preview = imports.save(admin, task.id(), second.version(), 3, config, null);
        assertThat(preview.batch().validRows()).isEqualTo(2);
        assertThat(preview.batch().failedRows()).isEqualTo(1);
        assertThat(preview.batch().rows().get(1).errorsJson()).contains("文件内前序记录重复");
        assertThat(imports.confirm(admin, preview.batch().batchId(), List.of(2, 4)).importedRows())
                .isEqualTo(2);
        first.put("月账期", "202702");
        placeholders.put("月账期", "202702");
        assertThat(concurrent(first, placeholders)).containsExactlyInAnyOrder(true, false);
    }

    List<Boolean> concurrent(Map<String, String> first, Map<String, String> second)
            throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (var values : List.of(first, second))
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    try {
                                        service.create(admin, values);
                                        return true;
                                    } catch (BusinessException e) {
                                        return false;
                                    }
                                }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
