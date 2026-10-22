package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectExportTaskRepository;
import com.pluto.ndi.projects.service.*;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(classes = {NdiApplication.class, ReviewerProvinceScopeIT.TimeConfiguration.class}, properties = {
        "ndi.auth.mode=SESSION", "ndi.auth.secure-cookie=false", "ndi.bootstrap.admin-login=scope-admin",
        "ndi.bootstrap.admin-password=scope-admin-password", "ndi.projects.inactivity.enabled=false", "ndi.approvals.reminders-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ReviewerProvinceScopeIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary ScopeClock scopeClock() { return new ScopeClock(); }
    }
    static class ScopeClock extends Clock {
        private Instant value = Instant.parse("2026-01-15T03:00:00Z");
        void move(YearMonth month, int day) { value = month.atDay(day).atTime(3, 0).toInstant(ZoneOffset.UTC); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return value; }
    }
    private static final AtomicInteger MONTH = new AtomicInteger();
    @Autowired JdbcTemplate jdbc;
    @Autowired ScopeClock clock;
    @Autowired IdentityAdministrationService administrators;
    @Autowired IdentityDirectory identities;
    @Autowired IdentitySessionService sessions;
    @Autowired ProjectService projects;
    @Autowired ProjectContextService context;
    @Autowired ProjectWorkbenchService workbench;
    @Autowired ProjectMonthlySnapshotService monthly;
    @Autowired ProjectMapService maps;
    @Autowired ProjectActivityService activities;
    @Autowired ProjectProgressService progresses;
    @Autowired ProjectApprovalService approvals;
    @Autowired ProjectApprovalInboxService inbox;
    @Autowired ProjectApprovalNotificationService notifications;
    @Autowired ProjectExportService exports;
    @Autowired ProjectExportTaskRepository exportTasks;
    @Autowired ProjectInactivityService inactivity;
    @Autowired ProjectImportService imports;
    @Autowired ProjectNotificationSummaryService notificationCounts;
    private final PageRequest page = PageRequest.of(0, 20);
    private final String password = "reviewer-test-password";
    UUID admin;
    AdminAccountView reviewer;
    Region a;
    Region b;
    Region c;
    YearMonth month;

    @BeforeEach void seed() {
        month = YearMonth.of(2026, 1).plusMonths(MONTH.getAndIncrement()); clock.move(month, 15);
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE login_name <> 'scope-admin'");
        jdbc.update("UPDATE organization_province SET enabled=false");
        admin = UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_user_account WHERE login_name='scope-admin'", String.class));
        a = region("广东省"); b = region("浙江省"); c = region("山东省");
        String phone = "138" + String.format("%08d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000));
        reviewer = administrators.create(admin, new AdminAccountCommand(phone, "多省接口人", null,
                List.of("INTERFACE_REVIEWER"), null, null), password);
        reviewer = mapping(reviewer, a.province(), b.province());
    }

    @Test void readsOnlyAssignedProvincesAcrossListsDetailsCurrentStatisticsMapAndActivities() {
        assertThat(projects.findProjects(reviewer.id(), page).getContent()).extracting(ProjectSummary::id)
                .containsExactlyInAnyOrder(a.project(), b.project());
        assertThat(projects.findDetail(reviewer.id(), b.project()).id()).isEqualTo(b.project());
        denied(() -> projects.findDetail(reviewer.id(), c.project()));
        assertThat(projects.findProjects(reviewer.id(), new ProjectQuery(null, null, null, null, c.province(), null, true), page)).isEmpty();
        assertThat(workbench.overview(reviewer.id(), false).projectCount()).isEqualTo(2);
        assertThat(workbench.overview(reviewer.id(), false).totalExpectedAmount()).isEqualByComparingTo("200");
        assertThat(workbench.breakdown(reviewer.id(), false).cities()).extracting(ProjectWorkbenchBreakdown.CityTotal::cityId)
                .containsExactlyInAnyOrder(a.city(), b.city());
        var map = maps.current(reviewer.id(), null, false, null);
        assertThat(map.regions()).extracting(ProjectMapView.Region::id).containsExactlyInAnyOrder(a.province(), b.province());
        assertThat(maps.current(reviewer.id(), b.province(), false, null).regions()).extracting(ProjectMapView.Region::id).containsExactly(b.city());
        denied(() -> maps.current(reviewer.id(), c.province(), false, null));
        maps.requireBoundaryAccess(reviewer.id(), "100000"); maps.requireBoundaryAccess(reviewer.id(), "330000");
        denied(() -> maps.requireBoundaryAccess(reviewer.id(), "370000"));
        assertThat(activities.recent(reviewer.id())).extracting(ProjectRecentActivity::projectId)
                .contains(a.project(), b.project()).doesNotContain(c.project());
        assertThat(context.provinceLeaders(reviewer.id(), b.province())).extracting(ProjectUser::id).contains(b.leader());
        denied(() -> context.provinceLeaders(reviewer.id(), c.province()));
    }

    @Test void authorizesApprovalsImportAndCollaborationButNotProjectCreationOrMajorChangeInitiation() {
        var actor = identities.findProjectActor(reviewer.id()).orElseThrow();
        for (var region : List.of(a, b)) {
            assertThat(identities.canImportProject(actor, region.province(), region.city())).isTrue();
            assertThat(identities.canUploadProjectDocument(actor, region.province(), region.city())).isTrue();
            assertThat(identities.canCreateProject(actor, region.province(), region.city())).isFalse();
            assertThat(identities.canInitiateMajorChange(actor, region.province(), region.city(), ProjectInitiatorLevel.PROVINCE)).isFalse();
            assertThat(identities.isEligibleStageApprover(reviewer.id(), region.province())).isTrue();
            assertThat(identities.findEligibleStageApprovers(region.province())).extracting(ProjectUser::id).contains(reviewer.id());
        }
        assertThat(identities.canImportProject(actor, c.province(), c.city())).isFalse();
        assertThat(identities.canUploadProjectDocument(actor, c.province(), c.city())).isFalse();
        progresses.append(reviewer.id(), b.project(), new AppendProgressCommand("跨省进展", "继续跟进", null));
        denied(() -> progresses.append(reviewer.id(), c.project(), new AppendProgressCommand("越权进展", "不得生效", null)));
        var first = change(a); var second = change(b);
        assertThat(inbox.counts(reviewer.id()).pendingCount()).isEqualTo(2);
        assertThat(inbox.findPending(reviewer.id(), page).getContent()).hasSize(2);
        assertThat(notifications.findMine(reviewer.id(), page).getContent()).hasSize(2);
        reviewer = mapping(reviewer, a.province());
        assertThat(inbox.counts(reviewer.id()).pendingCount()).isEqualTo(1);
        assertThat(inbox.findPending(reviewer.id(), page).getContent()).hasSize(1);
        assertThat(notifications.findMine(reviewer.id(), page).getContent()).hasSize(1);
        denied(() -> approvals.findApproval(reviewer.id(), second.id()));
        denied(() -> approvals.decide(reviewer.id(), second.id(), true, null));
        approvals.decide(reviewer.id(), first.id(), true, null);
        approvals.withdraw(b.leader(), second.id());
    }

    @Test void appliesTheSameProvinceSetToMonthlyReportsTrendsAndHistoricalMaps() {
        clock.move(month.plusMonths(1), 1); monthly.generateDue();
        var report = monthly.workbench(reviewer.id(), month, false);
        assertThat(report.projectCount()).isEqualTo(2);
        assertThat(report.totalExpectedAmount()).isEqualByComparingTo("200");
        assertThat(report.categoryTotals().network()).isEqualByComparingTo("120");
        assertThat(report.cities()).extracting(ProjectMonthlyWorkbench.CityTotal::cityId).containsExactlyInAnyOrder(a.city(), b.city());
        assertThat(monthly.trend(reviewer.id(), month, month, false)).singleElement().satisfies(point -> assertThat(point.projectCount()).isEqualTo(2));
        assertThat(monthly.stageTrend(reviewer.id(), month, month, false, List.of(ProjectStage.SOLUTION_EXCHANGE)))
                .singleElement().satisfies(point -> assertThat(point.projectCount()).isEqualTo(2));
        assertThat(maps.view(reviewer.id(), null, month, false, null).regions()).extracting(ProjectMapView.Region::id)
                .containsExactlyInAnyOrder(a.province(), b.province());
        denied(() -> maps.view(reviewer.id(), c.province(), month, false, null));
    }

    @Test void revokesSessionsAndFrozenExportsWhenScopeChangesAndNeverFallsBackToLegacyProvince() {
        var login = sessions.login(reviewer.loginName(), password);
        var task = exports.submit(reviewer.id(), ProjectQuery.all());
        var stored = exportTasks.findById(task.id()).orElseThrow();
        assertThat(stored.scope().provinceIds()).containsExactlyInAnyOrder(a.province(), b.province());
        var work = exports.claimPending().orElseThrow(); exports.verifyScope(work); exports.complete(task.id(), 2);
        jdbc.update("UPDATE identity_user_account SET province_id=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)", b.province().toString(), reviewer.id().toString());
        reviewer = mapping(reviewer, a.province());
        assertThat(sessions.resolveActor(login.token())).isEmpty();
        denied(() -> exports.verifyScope(work)); denied(() -> exports.download(reviewer.id(), task.id()));
        denied(() -> projects.findDetail(reviewer.id(), b.project()));
        denied(() -> context.provinceLeaders(reviewer.id(), b.province()));
        reviewer = mapping(reviewer);
        assertThat(identities.findReadableProjectScope(identities.findProjectActor(reviewer.id()).orElseThrow())).isEmpty();
        denied(() -> projects.findProjects(reviewer.id(), page));
        assertThat(inbox.counts(reviewer.id()).pendingCount()).isZero();
    }

    @Test void batchUpdatesAreAtomicVersionCheckedAndRejectDuplicatesInvalidOrDisabledReviewers() {
        String phone = "139" + String.format("%08d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000));
        var other = administrators.create(admin, new AdminAccountCommand(phone, "第二接口人", null, List.of("INTERFACE_REVIEWER"), null, null), password);
        var changes = List.of(new IdentityAdministrationService.ReviewerMappingCommand(reviewer.id(), reviewer.version(), List.of(c.province())),
                new IdentityAdministrationService.ReviewerMappingCommand(other.id(), other.version(), List.of(UUID.randomUUID())));
        denied(() -> administrators.saveReviewerMappings(admin, changes));
        assertThat(identities.findProjectUser(reviewer.id()).orElseThrow().reviewerProvinceIds()).containsExactlyInAnyOrder(a.province(), b.province());
        denied(() -> administrators.saveReviewerMappings(admin, List.of(new IdentityAdministrationService.ReviewerMappingCommand(reviewer.id(), reviewer.version(), List.of(a.province(), a.province())))));
        var stale = reviewer; reviewer = mapping(reviewer, a.province());
        denied(() -> administrators.saveReviewerMappings(admin, List.of(new IdentityAdministrationService.ReviewerMappingCommand(stale.id(), stale.version(), List.of(b.province())))));
        denied(() -> administrators.saveReviewerMappings(other.id(), List.of(new IdentityAdministrationService.ReviewerMappingCommand(other.id(), other.version(), List.of(c.province())))));
        var disabled = administrators.setEnabled(admin, other.id(), other.version(), false);
        denied(() -> administrators.saveReviewerMappings(admin, List.of(new IdentityAdministrationService.ReviewerMappingCommand(disabled.id(), disabled.version(), List.of(c.province())))));
    }

    @Test void inactivityEscalatesToMappedReviewerAndScopeRemovalHidesPriorCases() {
        for (var region : List.of(a, b)) {
            inactivity.trigger(admin, region.project(), 30); inactivity.trigger(admin, region.project(), 33);
        }
        assertThat(inactivity.findMine(reviewer.id(), false, page).getContent()).hasSize(2);
        assertThat(inactivity.auditHistory(reviewer.id(), b.project(), page)).isNotEmpty();
        reviewer = mapping(reviewer, a.province());
        assertThat(inactivity.findMine(reviewer.id(), false, page).getContent()).hasSize(1);
        denied(() -> inactivity.auditHistory(reviewer.id(), b.project(), page));
    }

    @Test void importPreflightAndConfirmationBothRespectCurrentProvinceMappings() throws Exception {
        var headers = List.of("省分", "地市", "客户名称", "客户所属行业", "项目名称", "省分负责人", "地市客户经理",
                "项目建设内容", "三化类别", "项目阶段", "项目总金额（万）", "网络化预计金额（万）", "格物预计金额（万）",
                "大数据预计金额（万）", "万悟预计金额（万）", "项目进展（" + month.getYear() + "年" + month.getMonthValue() + "月10日）");
        byte[] content;
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入数据");
            var header = sheet.createRow(0);
            for (int column = 0; column < headers.size(); column++) header.createCell(column).setCellValue(headers.get(column));
            var names = List.of("广东省", "浙江省", "山东省");
            for (int index = 0; index < names.size(); index++) {
                var values = List.of(names.get(index), "测试地市", "导入客户", "计算机、通信和其他电子设备制造业", "范围导入-" + UUID.randomUUID(),
                        "PROVINCE_REPORTER", "张经理", "客户平台建设", "数字化（格物）", "方案交流", "100", "", "50", "", "", "已开展交流");
                var row = sheet.createRow(index + 1);
                for (int column = 0; column < values.size(); column++) row.createCell(column).setCellValue(values.get(column));
            }
            workbook.write(output); content = output.toByteArray();
        }
        var preview = imports.preview(reviewer.id(), "多省范围导入.xlsx", content);
        assertThat(preview.validRows()).isEqualTo(2);
        assertThat(preview.failedRows()).isEqualTo(1);
        reviewer = mapping(reviewer, a.province());
        var confirmed = imports.confirm(reviewer.id(), preview.batchId());
        assertThat(confirmed.importedRows()).isEqualTo(1);
        assertThat(confirmed.failedRows()).isEqualTo(2);
    }

    @Test void nationalReadOnlyAndAdministratorRolesDoNotInheritActionableApprovalTasks() {
        var pending = change(a);
        assertThat(approvals.findApproval(reviewer.id(), pending.id()).canReview()).isTrue();
        reviewer = administrators.update(admin, reviewer.id(), reviewer.version(), new AdminAccountCommand(
                reviewer.loginName(), reviewer.displayName(), null, List.of("GROUP_REVIEWER"), null, null));
        assertThat(workbench.overview(reviewer.id(), false).projectCount()).isGreaterThanOrEqualTo(3);
        assertThat(inbox.counts(reviewer.id()).pendingCount()).isZero();
        assertThat(inbox.findPending(reviewer.id(), page)).isEmpty();
        assertThat(approvals.findApproval(reviewer.id(), pending.id()).canReview()).isFalse();
        denied(() -> approvals.decide(reviewer.id(), pending.id(), true, null));
        reviewer = administrators.update(admin, reviewer.id(), reviewer.version(), new AdminAccountCommand(
                reviewer.loginName(), reviewer.displayName(), null, List.of("SYSTEM_ADMIN"), null, null));
        assertThat(inbox.counts(reviewer.id()).pendingCount()).isZero();
        assertThat(inbox.findPending(reviewer.id(), page)).isEmpty();
        assertThat(approvals.findApproval(reviewer.id(), pending.id()).canReview()).isFalse();
        denied(() -> approvals.decide(reviewer.id(), pending.id(), true, null));
    }

    @Test void unreadMessagesAndActionableTasksAreDistinctAndInactivityCountsDeduplicateNotices() {
        var first = change(a); change(b);
        long rows = jdbc.queryForObject("SELECT COUNT(*) FROM project_approval_notification", Long.class);
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(2, 2, 0, 2, 0));
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(2, 2, 0, 2, 0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_approval_notification", Long.class)).isEqualTo(rows);
        var notice = notifications.findMine(reviewer.id(), page, true).getContent().getFirst();
        var read = notifications.markRead(reviewer.id(), notice.id());
        clock.move(month, 16);
        assertThat(notifications.markRead(reviewer.id(), notice.id()).readAt()).isEqualTo(read.readAt());
        assertThat(notifications.findMine(reviewer.id(), page, true).getTotalElements()).isEqualTo(1);
        assertThat(notifications.findMine(reviewer.id(), page).getTotalElements()).isEqualTo(2);
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(1, 1, 0, 2, 0));
        inactivity.trigger(admin, a.project(), 30); inactivity.trigger(admin, a.project(), 33);
        var caseId = inactivity.findMine(reviewer.id(), false, page).getContent().getFirst().id();
        jdbc.update("INSERT INTO project_inactivity_notice (id,case_id,recipient_id,kind,created_at) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'GROUP',CURRENT_TIMESTAMP(6))",
                UUID.randomUUID().toString(), caseId.toString(), reviewer.id().toString());
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(2, 1, 1, 2, 1));
        assertThat(inactivity.findMine(reviewer.id(), false, page).getTotalElements()).isEqualTo(1);
        approvals.decide(reviewer.id(), first.id(), true, null);
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(1, 1, 0, 1, 0));
        for (var unread : notifications.findMine(reviewer.id(), page, true)) notifications.markRead(reviewer.id(), unread.id());
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(0, 0, 0, 1, 0));
    }

    @Test void scopeChangesFilterUnreadCountsAndPreventMarkingHiddenOrForeignNotifications() {
        change(a); var second = change(b);
        var hidden = notifications.findMine(reviewer.id(), page, true).stream()
                .filter(item -> item.requestId().equals(second.id())).findFirst().orElseThrow();
        denied(() -> notifications.markRead(c.reporter(), hidden.id()));
        inactivity.trigger(admin, b.project(), 30); inactivity.trigger(admin, b.project(), 33);
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(3, 2, 1, 2, 1));
        reviewer = mapping(reviewer, a.province());
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(1, 1, 0, 1, 0));
        denied(() -> notifications.markRead(reviewer.id(), hidden.id()));
        assertThat(jdbc.queryForObject("SELECT read_at FROM project_approval_notification WHERE id=UUID_TO_BIN(?)", java.sql.Timestamp.class, hidden.id().toString())).isNull();
        reviewer = mapping(reviewer);
        assertThat(notificationCounts.counts(reviewer.id())).isEqualTo(new ProjectNotificationCounts(0, 0, 0, 0, 0));
        assertThat(inactivity.findMine(reviewer.id(), false, page)).isEmpty();
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE id=UUID_TO_BIN(?)", reviewer.id().toString());
        denied(() -> notificationCounts.counts(reviewer.id()));
    }

    private AdminAccountView mapping(AdminAccountView user, UUID... ids) {
        return administrators.saveReviewerMappings(admin, List.of(new IdentityAdministrationService.ReviewerMappingCommand(user.id(), user.version(), List.of(ids)))).getFirst();
    }
    private ApprovalSummary change(Region region) {
        return approvals.submitMajorChange(region.leader(), region.project(), new MajorChangeCommand(MajorChangeType.TOTAL_AMOUNT,
                ProjectInitiatorLevel.PROVINCE, reviewer.id(), "省分预算调整", null, new BigDecimal("110"), null));
    }
    private Region region(String name) {
        var province = UUID.randomUUID(); var city = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province (id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,?,true)", province.toString(), province.toString().replace("-", ""), name);
        jdbc.update("INSERT INTO organization_city (id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,'测试地市',true)", city.toString(), province.toString(), city.toString().replace("-", ""));
        UUID leader = user("PROVINCE_REPORTER", province, null); UUID reporter = user("CITY_REPORTER", province, city);
        var project = projects.createProject(reporter, new CreateProjectCommand(province, city, "多省项目-" + UUID.randomUUID(), "客户",
                "C25", "客户需求", new BigDecimal("100"), leader, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60"))), "初次进展", "继续跟进")).id();
        return new Region(province, city, leader, reporter, project);
    }
    private UUID user(String role, UUID province, UUID city) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,province_id,city_id,enabled) VALUES (UUID_TO_BIN(?),?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?),true)", id.toString(), id.toString(), role, province.toString(), city == null ? null : city.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id,role_code) VALUES (UUID_TO_BIN(?),?)", id.toString(), role);
        return id;
    }
    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) { assertThatThrownBy(action).isInstanceOf(BusinessException.class); }
    private record Region(UUID province, UUID city, UUID leader, UUID reporter, UUID project) { }
}
