package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.ReviewerScopeChanged;
import com.pluto.ndi.revenue.domain.*;
import com.pluto.ndi.revenue.domain.Revenue.*;
import com.pluto.ndi.revenue.mapper.RevenueMapper;
import com.pluto.ndi.revenue.service.*;
import com.pluto.ndi.revenue.util.*;
import com.pluto.ndi.shared.imports.ImportModels.Configuration;
import com.pluto.ndi.shared.web.BusinessException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class RevenueIT {
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
    @Autowired com.pluto.ndi.production.service.ProductionImportService productionImports;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired TransactionTemplate transactions;
    UUID province,
            otherProvince,
            city,
            otherCity,
            admin,
            group,
            ds,
            reviewer,
            otherReviewer,
            reporter,
            cityReporter,
            outsider;
    String provinceName;

    @BeforeEach
    void setup() {
        province = province("计收省");
        otherProvince = province("外省");
        provinceName =
                jdbc.queryForObject(
                        "SELECT province_name FROM organization_province WHERE id=UUID_TO_BIN(?)",
                        String.class,
                        province.toString());
        city = city(province);
        otherCity = city(otherProvince);
        admin = user("SYSTEM_ADMIN", null, null);
        group = user("GROUP_REVIEWER", null, null);
        ds = user("DS_TECH", null, null);
        reviewer = user("INTERFACE_REVIEWER", null, null);
        otherReviewer = user("INTERFACE_REVIEWER", null, null);
        scope(reviewer, province);
        scope(otherReviewer, otherProvince);
        reporter = user("PROVINCE_REPORTER", province, null);
        cityReporter = user("CITY_REPORTER", province, city);
        outsider = user("CITY_REPORTER", otherProvince, otherCity);
    }

    UUID province(String label) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO organization_province(id,province_code,province_name,enabled)"
                        + " VALUES(UUID_TO_BIN(?),?,?,TRUE)",
                id.toString(),
                id.toString().replace("-", ""),
                label + id);
        return id;
    }

    UUID city(UUID p) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO organization_city(id,province_id,city_code,city_name,enabled)"
                        + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'测试地市',TRUE)",
                id.toString(),
                p.toString(),
                id.toString().replace("-", ""));
        return id;
    }

    UUID user(String role, UUID p, UUID c) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " identity_user_account(id,login_name,display_name,province_id,city_id,enabled)"
                    + " VALUES(UUID_TO_BIN(?),?,'计收验收',UUID_TO_BIN(?),UUID_TO_BIN(?),TRUE)",
                id.toString(),
                "revenue-" + id,
                p == null ? null : p.toString(),
                c == null ? null : c.toString());
        jdbc.update("INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),?)", id.toString(), role);
        return id;
    }

    void scope(UUID user, UUID p) {
        jdbc.update(
                "INSERT INTO identity_reviewer_province(reviewer_id,province_id)"
                        + " VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
                user.toString(),
                p.toString());
    }

    Map<String, String> source() {
        return new LinkedHashMap<>(
                Map.of(
                        "月账期",
                        "202610",
                        "费用账期",
                        "202609",
                        "省份名称",
                        provinceName,
                        "地市/组织名称",
                        "非行政地市文本",
                        "合同编号",
                        "000" + UUID.randomUUID(),
                        "条款编号",
                        "001",
                        "条款金额",
                        "100.00",
                        RevenueFields.SOURCE_AMOUNT,
                        "10.00",
                        "项目名称",
                        "同名来源项目",
                        "业务号码/项目编号",
                        "000042"));
    }

    Recognition value(String amount) {
        return new Recognition(new BigDecimal(amount), "通报项目名称", "实施中", null);
    }

    Entry entry() {
        return service.create(admin, source());
    }

    RevenueMapper.Filter filter(String state) {
        return new RevenueMapper.Filter("2026-10", province.toString(), "", state, 0, 100);
    }

    @Test
    void creationScopesUniquenessAndMoneyPrecision() {
        var s = source();
        var e = service.create(group, s);
        assertThat(e.month()).isEqualTo("2026-10");
        assertThat(e.source().get("费用账期")).isEqualTo("2026-09");
        assertThat(e.source().get("业务号码/项目编号")).isEqualTo("000042");
        assertThatThrownBy(() -> service.create(ds, s)).isInstanceOf(BusinessException.class);
        s.put("月账期", "202611");
        assertThat(service.create(ds, s).id()).isNotEqualTo(e.id());
        for (var actor : List.of(reviewer, reporter, cityReporter))
            assertThatThrownBy(() -> service.create(actor, source()))
                    .isInstanceOf(BusinessException.class);
        var bad = source();
        bad.put("条款金额", "1.001");
        assertThatThrownBy(() -> service.create(admin, bad)).hasMessageContaining("两位小数");
        bad.put("条款金额", "-1");
        assertThatThrownBy(() -> service.create(admin, bad)).isInstanceOf(BusinessException.class);
        bad.put("条款金额", "0");
        bad.put("月账期", "202613");
        assertThatThrownBy(() -> service.create(admin, bad)).hasMessageContaining("账期");
        assertThat(service.detail(cityReporter, e.id()).id()).isEqualTo(e.id());
        assertThat(service.detail(otherReviewer, e.id()).canRecognize()).isFalse();
        assertThatThrownBy(() -> service.detail(outsider, e.id()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recognize(admin, e.id(), e.version(), value("1")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recognize(group, e.id(), e.version(), value("1")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approvalKeepsOldResultAndRejectWithdrawDoNotChangeTotals() {
        var e = entry();
        var pending = service.recognize(cityReporter, e.id(), 0, value("80"));
        assertThat(pending.state()).isEqualTo("PENDING");
        assertThat(pending.recognition()).isNull();
        assertThat(service.list(reporter, filter("")).summary().recognizedAmount())
                .isEqualByComparingTo("0");
        var r = service.requests(reviewer, e.id()).getFirst();
        assertThat(r.approverId()).isEqualTo(reviewer);
        assertThatThrownBy(() -> service.recognize(reviewer, e.id(), pending.version(), value("2")))
                .isInstanceOf(BusinessException.class);
        var firstRequest = r;
        assertThatThrownBy(
                        () ->
                                service.decide(
                                        otherReviewer,
                                        firstRequest.id(),
                                        pending.version(),
                                        firstRequest.version(),
                                        "APPROVED",
                                        ""))
                .isInstanceOf(BusinessException.class);
        var approved =
                service.decide(reviewer, r.id(), pending.version(), r.version(), "APPROVED", "");
        assertThat(approved.recognition().amount()).isEqualByComparingTo("80");
        assertThat(approved.sourceAmount()).isEqualByComparingTo("10");
        var change = service.recognize(reporter, e.id(), approved.version(), value("90"));
        assertThat(change.state()).isEqualTo("CHANGE_PENDING");
        assertThat(change.recognition().amount()).isEqualByComparingTo("80");
        r =
                service.requests(reviewer, e.id()).stream()
                        .filter(x -> x.status().equals("PENDING"))
                        .findFirst()
                        .orElseThrow();
        var changeRequest = r;
        assertThatThrownBy(
                        () ->
                                service.decide(
                                        reviewer,
                                        changeRequest.id(),
                                        change.version(),
                                        changeRequest.version(),
                                        "REJECTED",
                                        ""))
                .hasMessageContaining("意见");
        var rejected =
                service.decide(
                        reviewer, r.id(), change.version(), r.version(), "REJECTED", "金额需核对");
        assertThat(rejected.recognition().amount()).isEqualByComparingTo("80");
        var next = service.recognize(reporter, e.id(), rejected.version(), value("0"));
        r =
                service.requests(reviewer, e.id()).stream()
                        .filter(x -> x.status().equals("PENDING"))
                        .findFirst()
                        .orElseThrow();
        var withdrawn =
                service.decide(reporter, r.id(), next.version(), r.version(), "WITHDRAWN", "");
        assertThat(withdrawn.recognition().amount()).isEqualByComparingTo("80");
        var direct = service.recognize(reviewer, e.id(), withdrawn.version(), value("0"));
        assertThat(direct.recognition().amount()).isEqualByComparingTo("0");
        assertThat(service.history(reporter, e.id())).hasSize(2);
        assertThat(service.list(reporter, filter("")).summary().recognizedAmount())
                .isEqualByComparingTo("0");
        assertThat(service.notices(reporter)).isNotEmpty();
        var n = service.notices(reporter).getFirst();
        service.read(reporter, n.id());
        assertThat(
                        service.notices(reporter).stream()
                                .filter(x -> x.id().equals(n.id()))
                                .findFirst()
                                .orElseThrow()
                                .readAt())
                .isNotNull();
    }

    @Test
    void organizationDirectRecognitionHasNoProvincePermissionLeak() {
        var s = source();
        s.put("省份名称", "--");
        s.put("地市/组织名称", "具体组织全称");
        var e = service.create(admin, s);
        assertThat(e.specificOrganization()).isTrue();
        assertThat(e.provinceId()).isNull();
        assertThatThrownBy(() -> service.detail(reporter, e.id()))
                .isInstanceOf(BusinessException.class);
        var rec = new Recognition(new BigDecimal("99"), "组织计收", "已交付", province);
        var saved = service.recognize(otherReviewer, e.id(), 0, rec);
        assertThat(saved.state()).isEqualTo("RECOGNIZED");
        assertThat(service.requests(otherReviewer, e.id())).isEmpty();
        assertThatThrownBy(() -> service.detail(cityReporter, e.id()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recognize(reviewer, e.id(), saved.version(), value("101")))
                .hasMessageContaining("条款金额");
        assertThatThrownBy(() -> service.recognize(reviewer, e.id(), saved.version(), value("-1")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () -> service.recognize(reviewer, e.id(), saved.version(), value("1.001")))
                .hasMessageContaining("两位小数");
    }

    @Test
    void missingAndDisabledReviewerBlocksSubmissionAndHandoverTransfersPending() {
        var e = entry();
        jdbc.update(
                "UPDATE identity_user_account SET enabled=FALSE WHERE id=UUID_TO_BIN(?)",
                reviewer.toString());
        assertThatThrownBy(() -> service.recognize(reporter, e.id(), 0, value("1")))
                .hasMessageContaining("接口人");
        jdbc.update(
                "UPDATE identity_user_account SET enabled=TRUE WHERE id=UUID_TO_BIN(?)",
                reviewer.toString());
        var pending = service.recognize(reporter, e.id(), 0, value("1"));
        var before = service.requests(reporter, e.id()).getFirst();
        transactions.executeWithoutResult(
                status -> {
                    jdbc.update(
                            "DELETE FROM identity_reviewer_province WHERE"
                                    + " province_id=UUID_TO_BIN(?)",
                            province.toString());
                    scope(otherReviewer, province);
                    events.publishEvent(new ReviewerScopeChanged(admin, Set.of(province)));
                });
        var after = service.requests(reporter, e.id()).getFirst();
        assertThat(after.approverId()).isEqualTo(otherReviewer);
        assertThat(after.version()).isEqualTo(before.version() + 1);
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(service.pending(reviewer)).isEmpty();
        assertThat(service.pending(otherReviewer)).extracting(Request::id).contains(after.id());
        assertThatThrownBy(
                        () ->
                                service.decide(
                                        reviewer,
                                        before.id(),
                                        pending.version(),
                                        before.version(),
                                        "APPROVED",
                                        ""))
                .isInstanceOf(BusinessException.class);
        assertThat(
                        service.decide(
                                        otherReviewer,
                                        after.id(),
                                        pending.version(),
                                        after.version(),
                                        "APPROVED",
                                        "")
                                .recognition()
                                .amount())
                .isEqualByComparingTo("1");
    }

    @Test
    void concurrentWritesAndSubmissionsHaveOnlyOneWinner() throws Exception {
        var s = source();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    try {
                                        service.create(admin, s);
                                        return true;
                                    } catch (BusinessException e) {
                                        return false;
                                    }
                                }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                            futures.get(0).get(10, TimeUnit.SECONDS)
                                    ^ futures.get(1).get(10, TimeUnit.SECONDS))
                    .isTrue();
            var e = entry();
            var gate = new CountDownLatch(1);
            var submits = new ArrayList<Future<Boolean>>();
            for (var actor : List.of(reporter, cityReporter))
                submits.add(
                        pool.submit(
                                () -> {
                                    gate.await();
                                    try {
                                        service.recognize(actor, e.id(), 0, value("1"));
                                        return true;
                                    } catch (BusinessException ex) {
                                        return false;
                                    }
                                }));
            gate.countDown();
            assertThat(
                            submits.get(0).get(10, TimeUnit.SECONDS)
                                    ^ submits.get(1).get(10, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(service.requests(reviewer, e.id())).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void zeroLimitMissingSourceAndUnknownRecognitionFieldsRemainDistinct() {
        var s = source();
        s.put("条款金额", "0");
        s.put(RevenueFields.SOURCE_AMOUNT, "0");
        var e = service.create(admin, s);
        assertThatThrownBy(() -> service.recognize(reviewer, e.id(), 0, value("0.01")))
                .hasMessageContaining("条款金额");
        var recognized = service.recognize(reviewer, e.id(), 0, value("0"));
        assertThat(recognized.recognition()).isNotNull();
        assertThat(recognized.recognition().amount()).isEqualByComparingTo("0");
        var missing = source();
        missing.put(RevenueFields.SOURCE_AMOUNT, "--");
        assertThatThrownBy(() -> service.create(admin, missing)).hasMessageContaining("必填");
        var injected = source();
        injected.put("认定金额", "50");
        assertThatThrownBy(() -> service.create(admin, injected)).hasMessageContaining("来源字段");
        assertThat(service.requestPage(outsider, "history", 0, 20).items()).isEmpty();
    }

    @Test
    void moduleTasksNeverOverwriteAnotherModulesTaskForSameActor() throws Exception {
        var bytes = workbook(List.of(source()), false);
        var revenue = imports.upload(admin, "计收.xlsx", bytes, null, null);
        var production = productionImports.upload(admin, "排产.xlsx", bytes, null, null);
        assertThat(production.id()).isNotEqualTo(revenue.id());
        assertThat(imports.current(admin).id()).isEqualTo(revenue.id());
        productionImports.discard(admin, production.id(), production.version());
        assertThat(imports.current(admin).id()).isEqualTo(revenue.id());
        imports.discard(admin, revenue.id(), revenue.version());
        assertThat(imports.current(admin)).isNull();
        assertThat(productionImports.current(admin)).isNull();
    }

    byte[] workbook(List<Map<String, String>> rows, boolean numeric) throws Exception {
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("来源数据");
            var header = sheet.createRow(0);
            for (int i = 0; i < RevenueFields.FIELDS.size(); i++)
                header.createCell(i).setCellValue(RevenueFields.FIELDS.get(i));
            for (int r = 0; r < rows.size(); r++) {
                var row = sheet.createRow(r + 1);
                for (int c = 0; c < RevenueFields.FIELDS.size(); c++) {
                    var field = RevenueFields.FIELDS.get(c);
                    row.createCell(c).setCellValue(rows.get(r).getOrDefault(field, ""));
                }
            }
            if (numeric) {
                var row = sheet.getRow(1);
                var date = row.getCell(RevenueFields.FIELDS.indexOf("审签日期"));
                date.setCellValue(LocalDate.of(2026, 10, 1));
                var style = wb.createCellStyle();
                style.setDataFormat(wb.createDataFormat().getFormat("yyyy/m/d"));
                date.setCellStyle(style);
                var id = row.getCell(RevenueFields.FIELDS.indexOf("业务号码/项目编号"));
                id.setCellValue(42);
                var text = wb.createCellStyle();
                text.setDataFormat(wb.createDataFormat().getFormat("000000"));
                id.setCellStyle(text);
            }
            sheet.createRow(rows.size() + 1);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void importWizardUsesAll33SourceFieldsRejectsDuplicatesAndPreservesExcelTypes()
            throws Exception {
        var s = source();
        var bad = new LinkedHashMap<>(s);
        bad.put("条款编号", "");
        var bytes = workbook(List.of(s, s, bad), true);
        assertThatThrownBy(() -> imports.upload(reviewer, "来源.xlsx", bytes, null, null))
                .isInstanceOf(BusinessException.class);
        var task = imports.upload(admin, "来源.xlsx", bytes, null, null);
        assertThatThrownBy(() -> imports.save(admin, task.id(), 0, 3, task.configuration(), null))
                .isInstanceOf(BusinessException.class);
        var mapping = new LinkedHashMap<String, String>();
        RevenueFields.FIELDS.forEach(f -> mapping.put(f, f));
        var full = new Configuration("来源数据", 1, mapping);
        var second = imports.save(admin, task.id(), 0, 2, full, null);
        var missing = new LinkedHashMap<>(mapping);
        missing.put("条款编号", "");
        assertThatThrownBy(
                        () ->
                                imports.save(
                                        admin,
                                        task.id(),
                                        second.version(),
                                        3,
                                        new Configuration("来源数据", 1, missing),
                                        null))
                .hasMessageContaining("条款编号");
        var preview = imports.save(admin, task.id(), second.version(), 3, full, null);
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.batch().failedRows()).isEqualTo(2);
        assertThat(imports.current(admin).step()).isEqualTo(3);
        assertThatThrownBy(() -> imports.batch(group, preview.batch().batchId()))
                .isInstanceOf(BusinessException.class);
        var fixed = imports.revise(admin, preview.batch().batchId(), 4, 0, Map.of("条款编号", "002"));
        assertThat(fixed.validRows()).isEqualTo(2);
        var completed = imports.confirm(admin, preview.batch().batchId(), List.of(2, 4));
        assertThat(completed.importedRows()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM system_audit_event WHERE"
                                    + " resource_id=UUID_TO_BIN(?) AND"
                                    + " resource_type='REVENUE_IMPORT' AND"
                                    + " event_type='REVENUE_IMPORT_COMPLETED'",
                                Integer.class,
                                completed.batchId().toString()))
                .isEqualTo(1);
        assertThat(completed.skippedRows()).isEqualTo(1);
        assertThat(imports.current(admin)).isNull();
        var imported = service.detail(admin, completed.rows().getFirst().recordId());
        assertThat(imported.source().get("业务号码/项目编号")).isEqualTo("000042");
        assertThat(imported.source().get("审签日期")).isEqualTo("2026-10-01");
        assertThatThrownBy(() -> imports.confirm(admin, preview.batch().batchId(), List.of(2)))
                .isInstanceOf(BusinessException.class);
        try (var book =
                new XSSFWorkbook(
                        new ByteArrayInputStream(
                                RevenueExcel.template(service.reference(admin))))) {
            assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 33);
            assertThat(book.getSheetAt(0).getDataValidations()).hasSize(1);
            assertThat(book.getSheetAt(0).getRow(0).getCell(12).getStringCellValue())
                    .isEqualTo("条款编号");
        }
    }
}
