package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.ReviewerScopeChanged;
import com.pluto.ndi.production.domain.ProductionPlan;
import com.pluto.ndi.production.domain.SchedulingOverview;
import com.pluto.ndi.production.service.ProductionPlanService;
import com.pluto.ndi.production.service.SchedulingOverviewService;
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
    @Autowired RevenueMapper mapper;
    @Autowired RevenueAccessService revenueAccess;
    @Autowired com.pluto.ndi.projects.GewuContractReport gewuContracts;
    @Autowired com.pluto.ndi.organization.service.RegionAdcodeCatalog regionAdcodes;
    @Autowired SchedulingOverviewService schedulingOverview;
    @Autowired ProductionPlanService plans;
    @Autowired com.pluto.ndi.revenue.service.RevenueOrganizationService organizationAssignments;
    @Autowired RevenueImportService imports;
    @Autowired com.pluto.ndi.production.service.ProductionImportService productionImports;
    @Autowired JdbcTemplate jdbc;
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
        var result =
                new LinkedHashMap<>(
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
        result.put("三化分类", "数字化");
        result.put("产品名称", "验收产品");
        result.putIfAbsent("费用账期", "202610");
        result.putIfAbsent("业务号码/项目编号", "000042");
        result.put("自然客户名称", "验收自然客户");
        return result;
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
    void annualCockpitUsesEffectiveIntegratedAttributionAndScopedContractMatching() {
        int year = 2077;
        var fixed = java.time.Clock.fixed(java.time.Instant.parse("2077-06-21T00:00:00Z"), java.time.ZoneOffset.UTC);
        var annualOverview = new RevenueAnnualOverviewService(revenueAccess, mapper, gewuContracts, regionAdcodes, fixed);
        String fee = year + "-02";
        String matched = "ANNUAL-" + UUID.randomUUID();
        String other = "ANNUAL-" + UUID.randomUUID();
        var jiangsu = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM organization_province WHERE province_code='320000'",
                (row, index) -> UUID.fromString(row.getString(1)));
        addAnnualProject(matched, fee, "COLLECTION", "GEWU", "10");
        addAnnualProject(other, fee, "COLLECTION", "GEWU", "0");
        addAnnualProject("OTHER-" + matched, fee, "SOLUTION_EXCHANGE", "GEWU", "99");
        addAnnualProject("PRODUCT-" + matched, fee, "COLLECTION", "NETWORK", "99");
        addAnnualProject("YEAR-" + matched, (year - 1) + "-02", "COLLECTION", "GEWU", "99");

        var first = annualEntry(matched, fee);
        var approved = approve(reviewer, first, new Recognition(new BigDecimal("120000"), "共同通报名称", "实施中", jiangsu));
        service.recognize(reviewer, first.id(), approved.version(), new Recognition(BigDecimal.ONE, "修改待审", "已上线", province), group);
        approve(reviewer, annualEntry(matched, year + "-03"),
                new Recognition(new BigDecimal("30000"), "共同通报名称", "实施中", jiangsu));
        var pending = annualEntry(matched, year + "-04");
        service.recognize(reviewer, pending.id(), pending.version(), value("999999"), group);
        annualEntry(matched, year + "-05");
        for (String organization : List.of("数科", "数智")) {
            approve(reviewer, annualEntry("ORG-" + UUID.randomUUID(), fee),
                    new Recognition(new BigDecimal("20000"), "组织计收", "实施中", null, organization));
        }
        approve(reviewer, annualEntry("YEAR-" + matched, (year - 1) + "-03"),
                new Recognition(new BigDecimal("999999"), "跨年", "实施中", jiangsu));
        var result = annualOverview.load(admin);
        assertThat(result.total().total()).isEqualByComparingTo("19");
        assertThat(result.months().get(1).money().total()).isEqualByComparingTo("16");
        assertThat(result.months().get(2).money().total()).isEqualByComparingTo("3");
        assertThat(result.coveredProvinces()).isEqualTo(1);
        assertThat(result.gewu().signedCount()).isEqualTo(2);
        assertThat(result.gewu().recognizedCount()).isEqualTo(1);
        assertThat(result.gewu().signedAmount()).isEqualByComparingTo("10");
        assertThat(result.gewu().recognizedAmount()).isEqualByComparingTo("15");
        assertThat(result.gewu().amountGap()).isEqualByComparingTo("-5");
        assertThat(result.gewu().amountGapRate()).isEqualByComparingTo("-50");
        var js = result.regions().stream().filter(row -> row.key().equals(jiangsu.toString())).findFirst().orElseThrow();
        assertThat(js.gewu().signedCount()).isEqualTo(1);
        assertThat(js.adcode()).isEqualTo("320000");
        assertThat(result.regions().stream().filter(row -> row.organization())).hasSize(2).allSatisfy(row -> {
            assertThat(row.gewu()).isNull();
            assertThat(row.adcode()).isNull();
        });
        var primary = result.regions().stream().filter(row -> row.key().equals(province.toString())).findFirst().orElseThrow();
        assertThat(primary.gewu().signedCount()).isEqualTo(1);
        assertThat(primary.gewu().amountGapRate()).isNull();
        assertThat(annualOverview.load(cityReporter).gewu().recognizedCount()).isEqualTo(1);
        var out = annualOverview.load(outsider);
        assertThat(out.total().total()).isZero();
        assertThat(out.gewu().signedCount()).isZero();
        assertThat(annualOverview.load(reviewer).projectScope()).isEqualTo("PROVINCE");

        approve(reviewer, annualEntry(matched, year + "-06"),
                new Recognition(new BigDecimal("20000"), "共同通报名称", "实施中", otherProvince));
        var conflict = annualOverview.load(admin);
        assertThat(conflict.conflicts()).hasSize(1);
        assertThat(conflict.conflicts().getFirst().contract()).isEqualTo(matched);
        assertThat(conflict.gewu().recognizedCount()).isEqualTo(1);
        assertThat(conflict.gewu().recognizedAmount()).isEqualByComparingTo("17");
        assertThat(conflict.regions().stream().mapToLong(row -> row.gewu() == null ? 0 : row.gewu().signedCount()).sum())
                .isEqualTo(2);
    }

    private Entry annualEntry(String contract, String fee) {
        var fields = source();
        fields.put("合同编号", contract);
        fields.put("月账期", fee.substring(0, 4) + "-12");
        fields.put("费用账期", fee);
        return service.create(admin, fields);
    }

    private void addAnnualProject(String contract, String month, String stage, String category, String amount) {
        var id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO project_project(id,project_code,province_id,city_id,project_name,customer_name,customer_need,
                    total_amount,current_stage_code,city_account_manager_name,version_no,created_by,created_at,updated_by,
                    updated_at,attribution_month,contract_no,construction_content_raw)
                VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,'测试客户','统计验收',100,?,'测试经理',
                    0,UUID_TO_BIN(?),UTC_TIMESTAMP(),UUID_TO_BIN(?),UTC_TIMESTAMP(),?,?,'统计验收')
                """, id, id, province.toString(), city.toString(), id, stage,
                admin.toString(), admin.toString(), month + "-01", contract);
        jdbc.update("""
                INSERT INTO project_category_amount(id,project_id,category_code,expected_amount)
                VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(?),?,?)
                """, id, category, new BigDecimal(amount));
    }

    @Test
    void schedulingOverviewUsesFeeMonthsEffectiveRecognitionAndIndependentScopes() {
        String sameName = "跨省同名";
        var plan = plans.create(admin, cockpitPlan(sameName, "2090-01", province, city));
        plans.create(admin, cockpitPlan(sameName, "2090-02", otherProvince, otherCity));
        plans.create(admin, cockpitPlan(sameName, "2090-03", province, city));
        var deleted = plans.create(admin, cockpitPlan("已删除", "2090-01", province, city));
        plans.delete(admin, deleted.id(), deleted.version());
        assertThat(plan.bigDataAmount()).isEqualByComparingTo("1.5");

        var first = cockpitRecognition("2090-01", provinceName, "30000", sameName);
        var applied = approve(reviewer, first, new Recognition(new BigDecimal("30000"), sameName, "实施中", null));
        service.recognize(reviewer, first.id(), applied.version(), value("100000000"), group);

        String otherName = jdbc.queryForObject("SELECT province_name FROM organization_province WHERE id=UUID_TO_BIN(?)",
                String.class, otherProvince.toString());
        approve(otherReviewer, cockpitRecognition("2090-02", otherName, "60000", sameName),
                new Recognition(new BigDecimal("60000"), sameName, "实施中", null));
        var noFee = approve(reviewer, cockpitRecognition("2090-01", provinceName, "100000", "历史空费用账期"),
                new Recognition(new BigDecimal("100000"), "历史空费用账期", "实施中", null));
        jdbc.update("""
                UPDATE revenue_entry SET source_fields=JSON_REMOVE(source_fields,'$."费用账期"')
                WHERE id=UUID_TO_BIN(?)
                """, noFee.id().toString());
        approve(reviewer, cockpitRecognition("2090-02", "具体组织", "10000", sameName),
                new Recognition(new BigDecimal("10000"), sameName, "实施中", null));

        var result = schedulingOverview.load(admin, "2090-01", "2090-02");
        assertThat(result.plannedProjects()).isEqualTo(1);
        assertThat(result.recognizedProjects()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(new SchedulingOverview.Amounts("6", "10", "-4", "166.6667"));
        assertThat(result.categories().get(1).amounts().recognized()).isEqualTo("10");
        assertThat(result.categories().get(2).amounts().planned()).isEqualTo("3");
        assertThat(result.months().get(1).recognizedIncrease()).isEqualTo("4");
        assertThat(result.provinces()).extracting(SchedulingOverview.Province::name)
                .containsExactly(otherName, provinceName, "具体组织");

        var scoped = schedulingOverview.load(cityReporter, "2090-01", "2090-02");
        assertThat(scoped.productionScope()).isEqualTo("CITY");
        assertThat(scoped.revenueScope()).isEqualTo("PROVINCE");
        assertThat(scoped.total()).isEqualTo(new SchedulingOverview.Amounts("3", "3", "0", "100"));
        assertThat(scoped.months().get(1).amounts().rate()).isNull();
        assertThat(scoped.months().get(1).recognizedIncrease()).isEqualTo("-3");
        var interfaceView = schedulingOverview.load(reviewer, "2090-01", "2090-02");
        assertThat(interfaceView.total().planned()).isEqualTo("3");
        assertThat(interfaceView.total().recognized()).isEqualTo("10");
        assertThat(schedulingOverview.load(admin, "2090-09", "2090-09").empty()).isTrue();
        assertThatThrownBy(() -> schedulingOverview.load(UUID.randomUUID(), "2090-01", "2090-02"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> schedulingOverview.load(admin, "2090-02", "2090-01"))
                .isInstanceOf(BusinessException.class);
    }

    private ProductionPlan.Command cockpitPlan(String name, String month, UUID place, UUID district) {
        return new ProductionPlan.Command("统计验收", month, place, district, name, "验收客户", List.of(),
                new BigDecimal("1.5"), null, null, BigDecimal.ONE, new BigDecimal("0.5"),
                null, null, null, null, null);
    }

    private Entry cockpitRecognition(String fee, String place, String amount, String name) {
        var fields = source();
        fields.put("月账期", "2090-11");
        fields.put("费用账期", fee);
        fields.put("省份名称", place);
        fields.put(RevenueFields.SOURCE_AMOUNT, amount);
        fields.put("项目名称", name + UUID.randomUUID());
        return service.create(admin, fields);
    }

    @Test
    void dashboardCountsHistoricalUnclassifiedAndOnlyEffectiveRecognition() {
        var source = source();
        source.put("月账期", "2089-06");
        var legacy = service.create(admin, source);
        var applied = approve(reviewer, legacy, value("12.34"));
        service.recognize(reviewer, legacy.id(), applied.version(), value("99"), group);
        jdbc.update("""
                UPDATE revenue_entry SET source_fields=JSON_REMOVE(source_fields,'$."三化分类"')
                WHERE id=UUID_TO_BIN(?)
                """, legacy.id().toString());
        var unrecognized = source();
        unrecognized.put("月账期", "2089-06");
        var unassigned = service.create(admin, unrecognized);
        jdbc.update("UPDATE revenue_entry SET province_id=NULL, source_fields=JSON_REMOVE(source_fields, '$.\"省份名称\"') WHERE id=UUID_TO_BIN(?)",
                unassigned.id().toString());
        var national = service.dashboard(admin, "2089-06", "2089-06");
        assertThat(national.count()).isEqualTo(2);
        assertThat(national.amount()).isEqualTo("12.34");
        assertThat(national.categories()).anySatisfy(metric -> {
            assertThat(metric.label()).isEqualTo("未分类");
            assertThat(metric.count()).isEqualTo(1);
            assertThat(metric.amount()).isEqualTo("12.34");
        });
        assertThat(service.dashboard(cityReporter, "2089-06", "2089-06").count()).isEqualTo(1);
        assertThat(service.dashboard(admin, "2089-07", "2089-07").count()).isZero();

        var invalid = source();
        invalid.remove("三化分类");
        assertThatThrownBy(() -> service.create(admin, invalid)).hasMessageContaining("三化分类必填");
    }

    @Test
    void networkImportAndCategorySummaryEnforceTheSameRules() throws Exception {
        var network = source();
        network.put("三化分类", "网络化");
        assertThatThrownBy(() -> service.create(admin, network)).hasMessageContaining("仅允许系统管理员通过 Excel");
        assertThatThrownBy(() -> service.validateImport(admin, network)).hasMessageContaining("大于 0");
        var imported = new LinkedHashMap<>(network);
        imported.put("认定金额", "33");
        imported.put(RevenueImport.PROJECT, " ");
        imported.put("对应省分（集成）", provinceName);
        var digital = source();
        digital.put(RevenueImport.AMOUNT, "33");
        digital.put(RevenueImport.PROJECT, "数字化导入即认定");
        digital.put(RevenueImport.STATUS, "已上线");
        digital.put(RevenueImport.PROVINCE, provinceName);
        var intelligent = new LinkedHashMap<>(digital);
        intelligent.put("三化分类", "智能化");
        assertThat(service.validateImport(admin, intelligent).recognition().amount()).isEqualByComparingTo("33");
        var digitalWithoutName = new LinkedHashMap<>(digital);
        digitalWithoutName.remove(RevenueImport.PROJECT);
        var intelligentWithoutName = new LinkedHashMap<>(intelligent);
        intelligentWithoutName.put(RevenueImport.PROJECT, "  ");
        var incomplete = new LinkedHashMap<>(intelligent);
        incomplete.remove(RevenueImport.PROVINCE);
        assertThat(service.validateImport(admin, incomplete).recognition()).isNull();
        intelligent.put(RevenueImport.AMOUNT, "0");
        assertThat(service.validateImport(admin, intelligent).recognition()).isNull();
        var mapping = new LinkedHashMap<String, String>();
        imported.keySet().forEach(field -> mapping.put(field, field));
        mapping.put(RevenueImport.STATUS, RevenueImport.STATUS);
        var config = new Configuration("来源数据", 1, mapping);
        var groupFile = imports.upload(group, "混合.xlsx", recognitionWorkbook(imported, digital, digitalWithoutName, intelligentWithoutName), null, null);
        var groupMapping = imports.save(group, groupFile.id(), groupFile.version(), 2, config, null);
        var groupPreview = imports.save(group, groupFile.id(), groupMapping.version(), 3, config, null);
        assertThat(groupPreview.batch().rows().getFirst().errorsJson()).contains("仅允许系统管理员导入");
        assertThat(groupPreview.batch().validRows()).isEqualTo(1);
        assertThat(groupPreview.batch().rows().get(2).errorsJson()).contains("通报项目名称必填");
        assertThat(groupPreview.batch().rows().get(3).errorsJson()).contains("通报项目名称必填");
        assertThatThrownBy(() -> imports.confirm(group, groupPreview.batch().batchId(), List.of(2)))
                .isInstanceOf(BusinessException.class);
        var groupDone = imports.confirm(group, groupPreview.batch().batchId(), List.of(3));
        var digitalEntry = service.detail(group, groupDone.rows().stream().filter(row -> row.recordId() != null).findFirst().orElseThrow().recordId());
        assertThat(digitalEntry.state()).isEqualTo("RECOGNIZED");
        assertThat(digitalEntry.recognition().amount()).isEqualByComparingTo("33");
        assertThat(digitalEntry.recognition().reportedProjectName()).isEqualTo("数字化导入即认定");
        assertThat(digitalEntry.recognition().projectStatus()).isEqualTo("已上线");
        assertThat(digitalEntry.recognition().integratedProvinceId()).isEqualTo(province);

        var adminFile = imports.upload(admin, "网络化.xlsx", recognitionWorkbook(imported), null, null);
        var adminMapping = imports.save(admin, adminFile.id(), adminFile.version(), 2, config, null);
        var adminPreview = imports.save(admin, adminFile.id(), adminMapping.version(), 3, config, null);
        assertThat(adminPreview.batch().validRows()).isEqualTo(1);
        var done = imports.confirm(admin, adminPreview.batch().batchId(), List.of(2));
        var entry = service.detail(admin, done.rows().getFirst().recordId());
        assertThat(entry.state()).isEqualTo("RECOGNIZED");
        assertThat(entry.recognition().amount()).isEqualByComparingTo("33");
        assertThat(entry.recognition().reportedProjectName()).isEmpty();
        assertThat(service.detail(reviewer, entry.id()).canRecognize()).isFalse();
        assertThatThrownBy(() -> service.recognize(reviewer, entry.id(), entry.version(), value("1"), group))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.approvers(reviewer, entry.id())).isInstanceOf(BusinessException.class);
        var list = service.list(reporter, new RevenueMapper.Filter("2026-10", province.toString(),
                "", "RECOGNIZED", 0, 1, null, "网络化"));
        assertThat(list.items()).extracting(Entry::id).containsExactly(entry.id());
        assertThat(list.totalElements()).isEqualTo(1);
        assertThat(list.summary().total()).isEqualTo(1);
        assertThat(list.summary().recognizedAmount()).isEqualByComparingTo("33");
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
        assertThatThrownBy(() -> service.recognize(admin, e.id(), e.version(), value("1"), group))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recognize(group, e.id(), e.version(), value("1"), group))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void feeMonthParticipatesInUniquenessAndNormalizesHistoricalValues() {
        var source = source();
        var september = service.create(admin, source);
        jdbc.update("""
                UPDATE revenue_entry SET source_fields = JSON_SET(source_fields, '$."费用账期"', '202609')
                WHERE id = UUID_TO_BIN(?)
                """, september.id().toString());
        source.put("费用账期", "2026-09");
        var normalized = service.validate(admin, source);
        assertThat(service.duplicate(normalized)).isTrue();
        assertThatThrownBy(() -> mapper.insert(UUID.randomUUID(), admin, normalized, java.time.Instant.now()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        source.put("费用账期", "2026-08");
        assertThat(service.create(admin, source).id()).isNotEqualTo(september.id());
        source.remove("费用账期");
        assertThatThrownBy(() -> service.create(admin, source)).hasMessageContaining("费用账期必填");
        source.put("费用账期", " -- ");
        assertThatThrownBy(() -> service.create(admin, source)).isInstanceOf(BusinessException.class);
    }

    @Test
    void legacyUnassignedEntriesRemainReadableAndCanBeRecognized() {
        var values = source();
        values.put("月账期", "202611");
        values.put("自然客户名称", "未归属客户-" + UUID.randomUUID());
        var entry = service.create(admin, values);
        jdbc.update("""
                UPDATE revenue_entry SET province_id=NULL, contract_no='', clause_no='',
                  source_fields=JSON_REMOVE(source_fields, '$."省份名称"', '$."地市/组织名称"', '$."费用账期"',
                   '$."合同编号"', '$."业务号码/项目编号"', '$."产品名称"')
                WHERE id=UUID_TO_BIN(?)
                """, entry.id().toString());
        var saved = service.detail(admin, entry.id());
        assertThat(saved.provinceId()).isNull();
        assertThat(saved.specificOrganization()).isFalse();
        assertThat(saved.contractNo()).isEmpty();
        var unassigned = new RevenueMapper.Filter(null, "UNASSIGNED", values.get("自然客户名称"), null, 0, 20);
        assertThat(service.list(admin, unassigned).items()).extracting(Entry::id).containsExactly(saved.id());
        assertThat(service.list(reporter, unassigned).items()).isEmpty();
        assertThat(service.detail(reviewer, saved.id()).canRecognize()).isTrue();
        for (var actor : List.of(reporter, cityReporter, outsider))
            assertThatThrownBy(() -> service.detail(actor, saved.id())).isInstanceOf(BusinessException.class);
        assertThat(approve(reviewer, saved, value("5")).recognition().amount()).isEqualByComparingTo("5");
    }

    private Entry approve(UUID actor, Entry entry, Recognition value) {
        var pending = service.recognize(actor, entry.id(), entry.version(), value, group);
        var request = service.pending(group).stream()
                .filter(item -> item.id().equals(pending.pendingRequestId())).findFirst().orElseThrow();
        return service.decide(group, request.id(), pending.version(), request.version(), "APPROVED", "");
    }

    @Test
    void approvalKeepsOldResultAndRejectWithdrawDoNotChangeTotals() {
        var e = entry();
        for (var actor : List.of(reporter, cityReporter, otherReviewer, admin, group)) {
            assertThatThrownBy(() -> service.approvers(actor, e.id())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.recognize(actor, e.id(), e.version(), value("180"), group))
                    .isInstanceOf(BusinessException.class);
        }
        var unassignedGroup = user("GROUP_REVIEWER", null, null);
        var pending = service.recognize(reviewer, e.id(), 0, value("180"), group);
        assertThat(pending.state()).isEqualTo("PENDING");
        assertThat(pending.recognition()).isNull();
        assertThat(service.list(reporter, filter("")).summary().recognizedAmount()).isEqualByComparingTo("0");
        var r = service.pending(group).getFirst();
        assertThat(r.approverId()).isEqualTo(group);
        assertThat(service.counts(group).pending()).isEqualTo(1);
        assertThat(service.pending(unassignedGroup)).isEmpty();
        for (var actor : List.of(reviewer, unassignedGroup, admin)) {
            assertThatThrownBy(() -> service.decide(actor, r.id(), pending.version(), r.version(), "APPROVED", ""))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.recognize(reviewer, e.id(), pending.version(), value("2"), group))
                .isInstanceOf(BusinessException.class);
        var approved = service.decide(group, r.id(), pending.version(), r.version(), "APPROVED", "");
        assertThat(approved.recognition().amount()).isEqualByComparingTo("180");
        var change = service.recognize(reviewer, e.id(), approved.version(), value("90"), group);
        assertThat(change.state()).isEqualTo("CHANGE_PENDING");
        assertThat(change.recognition().amount()).isEqualByComparingTo("180");
        var request = service.pending(group).getFirst();
        assertThatThrownBy(() -> service.decide(group, request.id(), change.version(), request.version(), "REJECTED", ""))
                .hasMessageContaining("意见");
        var rejected = service.decide(group, request.id(), change.version(), request.version(), "REJECTED", "金额需核对");
        assertThat(rejected.recognition().amount()).isEqualByComparingTo("180");
        var next = service.recognize(reviewer, e.id(), rejected.version(), value("1"), group);
        var withdrawal = service.pending(group).getFirst();
        var withdrawn = service.decide(reviewer, withdrawal.id(), next.version(), withdrawal.version(), "WITHDRAWN", "");
        assertThat(withdrawn.recognition().amount()).isEqualByComparingTo("180");
        var applied = approve(reviewer, withdrawn, value("0.01"));
        assertThat(applied.recognition().amount()).isEqualByComparingTo("0.01");
        assertThat(service.history(reporter, e.id())).hasSize(2);
        assertThat(service.list(reporter, filter("")).summary().recognizedAmount()).isEqualByComparingTo("0.01");
        var notice = service.notices(reviewer).getFirst();
        service.read(reviewer, notice.id());
        assertThat(service.notices(reviewer)).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(notice.id());
            assertThat(item.readAt()).isNotNull();
        });
    }

    @Test
    void organizationRecognitionRequiresGroupReviewAndRemainsOutsideProvinceScope() {
        var s = source();
        s.put("省份名称", "--");
        s.put("地市/组织名称", "具体组织全称");
        s.put("三化分类", "智能化");
        var entry = service.create(admin, s);
        assertThat(entry.specificOrganization()).isTrue();
        assertThat(entry.provinceId()).isNull();
        assertThatThrownBy(() -> service.detail(reporter, entry.id())).isInstanceOf(BusinessException.class);
        var value = new Recognition(new BigDecimal("101"), "组织计收", "已交付", province);
        var saved = approve(otherReviewer, entry, value);
        assertThat(saved.recognition().amount()).isEqualByComparingTo("101");
        assertThat(service.requests(otherReviewer, entry.id())).hasSize(1);
        assertThatThrownBy(() -> service.detail(cityReporter, entry.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.recognize(reviewer, entry.id(), saved.version(), value("1.001"), group))
                .hasMessageContaining("两位小数");
    }

    @Test
    void organizationAssignmentFollowsExactNormalizedNameWithoutChangingSourceOrRecognition() {
        var hubei = jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM organization_province WHERE province_code='420000'
                """, (row, index) -> UUID.fromString(row.getString(1)));
        var hubeiLeader = user("PROVINCE_REPORTER", hubei, null);
        var hubeiCity = user("CITY_REPORTER", hubei, city(hubei));
        scope(reviewer, hubei);
        var values = source();
        values.put("省份名称", "具体组织");
        values.put("地市/组织名称", "联通（湖北）产业互联网有限公司");
        var item = service.create(admin, values);
        var approved = approve(reviewer, item, new Recognition(new BigDecimal("15"), "组织计收", "已交付", otherProvince));
        var key = organizationAssignments.list(admin).stream().filter(row -> row.name().equals(values.get("地市/组织名称")))
                .findFirst().orElseThrow().key();
        assertThatThrownBy(() -> organizationAssignments.list(reporter)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> organizationAssignments.update(reviewer, key, province)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.detail(hubeiLeader, item.id())).isInstanceOf(BusinessException.class);
        organizationAssignments.update(admin, key, hubei);

        var later = source();
        later.put("省份名称", "--");
        later.put("地市/组织名称", "　联通(湖北)产业互联网有限公司  ");
        var future = service.importEntry(admin, later);
        var distinct = source();
        distinct.put("省份名称", "具体组织");
        distinct.put("地市/组织名称", "联通(湖北)产业互联网有限公司分部");
        var different = service.create(admin, distinct);
        var filter = new RevenueMapper.Filter(null, hubei.toString(), "", "", 0, 20, "产业互联网");
        for (var actor : List.of(hubeiLeader, reviewer, admin)) {
            var listing = service.list(actor, filter);
            assertThat(listing.items()).extracting(Entry::id).containsExactlyInAnyOrder(item.id(), future.id());
            assertThat(listing.totalElements()).isEqualTo(2);
            assertThat(listing.summary().recognizedAmount()).isEqualByComparingTo("15");
        }
        assertThat(service.detail(hubeiLeader, item.id()).source()).containsEntry("省份名称", "具体组织");
        assertThat(service.detail(hubeiLeader, item.id()).recognition()).isEqualTo(approved.recognition());
        assertThat(service.detail(hubeiLeader, item.id()).canRecognize()).isFalse();
        assertThatThrownBy(() -> service.detail(hubeiCity, item.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.detail(hubeiLeader, different.id())).isInstanceOf(BusinessException.class);
        organizationAssignments.update(admin, key, otherProvince);
        assertThat(service.list(reviewer, filter).totalElements()).isZero();
        assertThatThrownBy(() -> service.detail(hubeiLeader, item.id())).isInstanceOf(BusinessException.class);
        var otherLeader = user("PROVINCE_REPORTER", otherProvince, null);
        assertThat(service.detail(otherLeader, item.id()).id()).isEqualTo(item.id());
        organizationAssignments.update(admin, key, null);
        assertThatThrownBy(() -> service.detail(otherLeader, item.id())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_audit_event
                WHERE event_type='REVENUE_ORGANIZATION_PROVINCE_CHANGED' AND actor_id=UUID_TO_BIN(?)
                """, Integer.class, admin.toString())).isEqualTo(3);
    }

    @Test
    void disabledOrSelfGroupApproverIsIneligibleAndRoleIsRecheckedAtDecision() {
        var entry = entry();
        jdbc.update("UPDATE identity_user_account SET enabled=FALSE WHERE id=UUID_TO_BIN(?)", group.toString());
        assertThat(service.approvers(reviewer, entry.id())).noneMatch(item -> item.id().equals(group));
        assertThatThrownBy(() -> service.recognize(reviewer, entry.id(), 0, value("1"), group))
                .hasMessageContaining("已启用的集团审核人");
        jdbc.update("UPDATE identity_user_account SET enabled=TRUE WHERE id=UUID_TO_BIN(?)", group.toString());
        jdbc.update("INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),'GROUP_REVIEWER')", reviewer.toString());
        assertThat(service.approvers(reviewer, entry.id())).noneMatch(item -> item.id().equals(reviewer));
        assertThatThrownBy(() -> service.recognize(reviewer, entry.id(), 0, value("1"), reviewer))
                .hasMessageContaining("本人");
        assertThat(service.approvers(reviewer, entry.id())).anyMatch(item -> item.id().equals(group));
        var pending = service.recognize(reviewer, entry.id(), 0, value("1"), group);
        var request = service.pending(group).getFirst();
        jdbc.update("DELETE FROM identity_user_role WHERE user_id=UUID_TO_BIN(?) AND role_code='GROUP_REVIEWER'",
                group.toString());
        assertThatThrownBy(() -> service.decide(group, request.id(), pending.version(), request.version(), "APPROVED", ""))
                .isInstanceOf(BusinessException.class);
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
            for (var actor : List.of(reviewer, reviewer))
                submits.add(
                        pool.submit(
                                () -> {
                                    gate.await();
                                    try {
                                        service.recognize(actor, e.id(), 0, value("1"), group);
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
    void positiveRecognitionWorksWithZeroSourceAndRejectsNonpositiveAmounts() {
        var s = source();
        s.put("条款金额", "0");
        s.put(RevenueFields.SOURCE_AMOUNT, "0");
        var e = service.create(admin, s);
        for (var invalidAmount : List.of("0", "-1")) {
            assertThatThrownBy(() -> service.recognize(reviewer, e.id(), 0, value(invalidAmount), group))
                    .hasMessageContaining("认定金额须大于零");
        }
        var recognized = approve(reviewer, e, value("0.01"));
        assertThat(recognized.recognition().amount()).isEqualByComparingTo("0.01");
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

    @SafeVarargs
    private final byte[] recognitionWorkbook(Map<String, String>... rows) throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook(List.of(rows), false)));
                var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var header = sheet.getRow(0);
            for (var field : List.of(RevenueImport.AMOUNT, RevenueImport.PROJECT, RevenueImport.STATUS, RevenueImport.PROVINCE)) {
                int column = header.getLastCellNum();
                header.createCell(column).setCellValue(field);
                for (int i = 0; i < rows.length; i++)
                    sheet.getRow(i + 1).createCell(column).setCellValue(rows[i].getOrDefault(field, ""));
            }
            book.write(out);
            return out.toByteArray();
        }
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
    void importsAdministrativeNamesWithoutTheirSuffixes() {
        for (var names : List.of(
                List.of("广东", "广州", "广东省", "广州市"),
                List.of("上海", "上海", "上海市", "上海市"),
                List.of("内蒙古", "呼和浩特", "内蒙古自治区", "呼和浩特市"))) {
            var values = source();
            values.put("省份名称", names.get(0));
            values.put("地市/组织名称", names.get(1));
            values.put("三化分类", "网络化");
            values.put(RevenueImport.AMOUNT, "20");
            values.put(RevenueImport.PROJECT, "通报项目");
            values.put(RevenueImport.PROVINCE, "内蒙古");
            var record = service.importEntry(admin, values);
            assertThat(record.source()).containsEntry("省份名称", names.get(2))
                    .containsEntry("地市/组织名称", names.get(3));
            assertThat(record.recognition().integratedProvinceId()).isEqualTo(
                    jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM organization_province WHERE province_code='150000'",
                            (rs, row) -> UUID.fromString(rs.getString(1))));
            assertThat(record.recognition().projectStatus()).isEmpty();
        }
        var organization = source();
        organization.put("省份名称", "广东");
        organization.put("地市/组织名称", "测试公司");
        assertThat(service.importEntry(admin, organization).source())
                .containsEntry("地市/组织名称", "测试公司");
    }

    @Test
    void invalidRecognitionIsReportedPerRowAndCanBeCorrected() throws Exception {
        var valid = source();
        var excessive = new LinkedHashMap<>(valid);
        excessive.put("合同编号", UUID.randomUUID().toString());
        excessive.put("三化分类", "网络化");
        excessive.put(RevenueImport.AMOUNT, "101");
        excessive.put(RevenueImport.PROJECT, "待修正项目");
        excessive.put(RevenueImport.PROVINCE, province.toString());
        excessive.put(RevenueImport.STATUS, "无效状态");
        var bytes = workbook(List.of(valid, excessive), false);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes));
                var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var header = sheet.getRow(0);
            for (var field : List.of(RevenueImport.AMOUNT, RevenueImport.PROJECT, RevenueImport.STATUS, RevenueImport.PROVINCE)) {
                int column = header.getLastCellNum();
                header.createCell(column).setCellValue(field);
                sheet.getRow(2).createCell(column).setCellValue(excessive.get(field));
            }
            book.write(out);
            bytes = out.toByteArray();
        }
        var task = imports.upload(admin, "预检.xlsx", bytes, null, null);
        var mapping = new LinkedHashMap<String, String>();
        RevenueImport.fields().forEach(field -> mapping.put(field, field));
        var configuration = new Configuration("来源数据", 1, mapping);
        var mapped = imports.save(admin, task.id(), task.version(), 2, configuration, null);
        var preview = imports.save(admin, task.id(), mapped.version(), 3, configuration, null);
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.batch().rows().get(1).errorsJson()).contains("项目状态须为实施中、已交付或已上线");
        var batch = preview.batch().batchId();
        assertThat(imports.current(admin).batch().batchId()).isEqualTo(batch);
        assertThat(imports.revise(admin, batch, 3, 0, Map.of(RevenueImport.PROVINCE, "无效省份")).rows().get(1).errorsJson())
                .contains("对应省分（集成）未匹配启用的省份");
        assertThat(imports.revise(admin, batch, 3, 1, Map.of(RevenueImport.PROVINCE, province.toString()))
                .rows().get(1).errorsJson()).contains("项目状态须为实施中、已交付或已上线");
        assertThat(imports.revise(admin, batch, 3, 2, Map.of(RevenueImport.STATUS, "已上线", RevenueImport.PROVINCE, "数智"))
                .validRows()).isEqualTo(2);
        var completed = imports.confirm(admin, batch, List.of(2, 3));
        assertThat(completed.importedRows()).isEqualTo(2);
        var recognition = service.detail(admin, completed.rows().get(1).recordId()).recognition();
        assertThat(recognition.amount()).isEqualByComparingTo("101");
        assertThat(recognition.projectStatus()).isEqualTo("已上线");
        assertThat(recognition.integratedOrganization()).isEqualTo("数智");
        assertThat(recognition.integratedProvinceId()).isNull();
    }

    @Test
    void importWizardUsesAll33SourceFieldsRejectsDuplicatesAndPreservesExcelTypes()
            throws Exception {
        var s = source();
        var bad = new LinkedHashMap<>(s);
        bad.put("条款编号", "");
        bad.put("条款金额", "");
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
        missing.put("条款金额", "");
        assertThatThrownBy(
                        () ->
                                imports.save(
                                        admin,
                                        task.id(),
                                        second.version(),
                                        3,
                                        new Configuration("来源数据", 1, missing),
                                        null))
                .hasMessageContaining("条款金额");
        var preview = imports.save(admin, task.id(), second.version(), 3, full, null);
        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.batch().failedRows()).isEqualTo(2);
        assertThat(imports.current(admin).step()).isEqualTo(3);
        assertThatThrownBy(() -> imports.batch(group, preview.batch().batchId()))
                .isInstanceOf(BusinessException.class);
        var fixed =
                imports.revise(
                        admin,
                        preview.batch().batchId(),
                        4,
                        0,
                        Map.of("条款编号", "--", "条款金额", "100"));
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
        var noClause =
                service.detail(
                        admin,
                        completed.rows().stream()
                                .filter(row -> row.rowNumber() == 4)
                                .findFirst()
                                .orElseThrow()
                                .recordId());
        assertThat(noClause.source().get("条款编号")).isEmpty();
        assertThatThrownBy(() -> service.create(admin, noClause.source()))
                .isInstanceOf(BusinessException.class);
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
            assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short) 37);
            assertThat(book.getSheetAt(0).getDataValidations()).hasSize(4);
            assertThat(book.getSheetAt(0).getRow(0).getCell(12).getStringCellValue())
                    .isEqualTo("条款编号");
        }
    }
}
