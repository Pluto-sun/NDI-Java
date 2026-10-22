package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectChangeField;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.service.CreateProjectCommand;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import com.pluto.ndi.projects.service.MajorChangeItem;
import com.pluto.ndi.projects.service.ProjectApprovalService;
import com.pluto.ndi.projects.service.ProjectMonthlySnapshotService;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = {NdiApplication.class, ProjectFieldChangesIT.ClockConfiguration.class}, properties = {
        "ndi.projects.inactivity.enabled=false", "ndi.approvals.reminders-enabled=false",
        "ndi.projects.export.initial-delay-ms=600000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProjectFieldChangesIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary MutableClock fieldChangesClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private Instant value;
        MutableClock() { moveTo(6, 15); }
        void moveTo(int month, int day) {
            value = LocalDate.of(2026, month, day).atTime(9, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private ProjectService projects;
    @Autowired private com.pluto.ndi.projects.service.ProjectProgressService progressService;
    @Autowired private com.pluto.ndi.projects.service.ProjectProfileService profileService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectApprovalService approvals;
    @Autowired private ProjectMonthlySnapshotService snapshots;
    @Autowired private MutableClock clock;
    private UUID province;
    private UUID city;
    private UUID reporter;
    private UUID leader;

    @BeforeEach
    void seed() {
        clock.moveTo(6, 15);
        jdbc.update("DELETE FROM project_monthly_snapshot_revision");
        jdbc.update("DELETE FROM project_monthly_snapshot");
        jdbc.update("UPDATE project_monthly_snapshot_control SET activation_month = '2026-06-01', last_completed_month = NULL");
        province = UUID.randomUUID(); city = UUID.randomUUID(); reporter = UUID.randomUUID(); leader = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province VALUES (UUID_TO_BIN(?), ?, '组合变更省', true)",
                province.toString(), province.toString().replace("-", ""));
        jdbc.update("INSERT INTO organization_city VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '组合变更市', true)",
                city.toString(), province.toString(), city.toString().replace("-", ""));
        user(reporter, "CITY_REPORTER", city);
        user(leader, "PROVINCE_REPORTER", null);
    }

    @Test
    void freezesBeforeValuesAndAppliesNameTotalAndCategoryAmountsTogether() throws Exception {
        var project = create("组合变更项目");
        var request = approvals.submitMajorChange(reporter, project, command(
                item(ProjectChangeField.PROJECT_NAME, "调整后的项目"), item(ProjectChangeField.TOTAL_AMOUNT, "150"),
                item(ProjectChangeField.NETWORK_AMOUNT, "80"), item(ProjectChangeField.GEWU_AMOUNT, "20")));
        assertThat(request.fieldChanges()).hasSize(4);
        assertThat(projects.findDetail(reporter, project).totalAmount()).isEqualByComparingTo("100.00");
        assertThat(category(project, "NETWORK")).isEqualByComparingTo("60.00");
        mvc.perform(get("/api/v1/projects/approvals/{id}", request.id()).header("X-NDI-Actor-Id", leader.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fieldChanges[2].originalValue").value("60.00"))
                .andExpect(jsonPath("$.data.fieldChanges[3].originalValue").isEmpty());
        approvals.decide(leader, request.id(), true, null);
        var detail = projects.findDetail(reporter, project);
        assertThat(detail.projectName()).isEqualTo("调整后的项目");
        assertThat(detail.totalAmount()).isEqualByComparingTo("150.00");
        assertThat(category(project, "NETWORK")).isEqualByComparingTo("80.00");
        assertThat(category(project, "GEWU")).isEqualByComparingTo("20.00");
        assertThat(detail.versionNo()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT CAST(JSON_EXTRACT(payload, '$.after.categoryAmounts.GEWU') AS DECIMAL(18,2)) FROM project_change_event WHERE project_id = UUID_TO_BIN(?) AND event_type = 'MAJOR_CHANGE_APPROVED'",
                BigDecimal.class, project.toString())).isEqualByComparingTo("20.00");
    }

    @Test
    void validatesTheCombinedTargetAndRejectsDuplicateFieldsAndForwardStages() throws Exception {
        var project = create("校验项目");
        for (var invalid : List.of(
                command(item(ProjectChangeField.NETWORK_AMOUNT, "90"), item(ProjectChangeField.GEWU_AMOUNT, "20")),
                command(item(ProjectChangeField.TOTAL_AMOUNT, "50")),
                command(item(ProjectChangeField.NETWORK_AMOUNT, "1.001")),
                command(item(ProjectChangeField.NETWORK_AMOUNT, "-1")),
                command(item(ProjectChangeField.NETWORK_AMOUNT, "10000000000000000")),
                command(item(ProjectChangeField.NETWORK_AMOUNT, "1e2")),
                command(item(ProjectChangeField.NETWORK_AMOUNT, "30"), item(ProjectChangeField.NETWORK_AMOUNT, "40")),
                command(item(ProjectChangeField.STAGE, "BIDDING")),
                command(item(ProjectChangeField.PROJECT_NAME, "校验项目")))) {
            assertThat(catchThrowable(() -> approvals.submitMajorChange(reporter, project, invalid)))
                    .isInstanceOf(BusinessException.class);
        }
        mvc.perform(post("/api/v1/projects/{id}/major-changes", project).header("X-NDI-Actor-Id", reporter.toString())
                        .contentType("application/json").content(json.writeValueAsString(command(
                                item(ProjectChangeField.TOTAL_AMOUNT, "50"), item(ProjectChangeField.NETWORK_AMOUNT, "40")))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.fieldChanges.length()").value(2));
        assertThat(projects.findDetail(reporter, project).totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectionWithdrawalAndConflictLeaveAllRequestedFieldsUnchanged() {
        var project = create("拒绝与冲突项目");
        var request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.NETWORK_AMOUNT, "50")));
        approvals.decide(leader, request.id(), false, "需重新确认金额");
        assertThat(category(project, "NETWORK")).isEqualByComparingTo("60.00");
        request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.NETWORK_AMOUNT, "50")));
        approvals.withdraw(reporter, request.id());
        assertThat(category(project, "NETWORK")).isEqualByComparingTo("60.00");
        request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.PROJECT_NAME, "不应写入"),
                item(ProjectChangeField.NETWORK_AMOUNT, "50")));
        jdbc.update("UPDATE project_category_amount SET expected_amount = 55 WHERE project_id = UUID_TO_BIN(?)", project.toString());
        assertThat(approvals.decide(leader, request.id(), true, null).status()).isEqualTo(ApprovalStatus.CONFLICT);
        assertThat(projects.findDetail(reporter, project).projectName()).isEqualTo("拒绝与冲突项目");
        assertThat(category(project, "NETWORK")).isEqualByComparingTo("55.00");
    }

    @Test
    void duplicateNameConflictDoesNotApplyAnyFinancialChange() {
        var project = create("改名前项目");
        create("已有目标名称");
        var request = approvals.submitMajorChange(reporter, project, command(
                item(ProjectChangeField.PROJECT_NAME, "已有目标名称"), item(ProjectChangeField.NETWORK_AMOUNT, "40")));
        assertThat(approvals.decide(leader, request.id(), true, null).status()).isEqualTo(ApprovalStatus.CONFLICT);
        assertThat(projects.findDetail(reporter, project).projectName()).isEqualTo("改名前项目");
        assertThat(category(project, "NETWORK")).isEqualByComparingTo("60.00");
    }

    @Test
    void rollbackClearsContractAndTerminationPreservesItInsideCombinedChanges() {
        var project = create("回退项目");
        forceCollection(project, "FIELD-ROLLBACK");
        var request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.STAGE, "BIDDING"),
                item(ProjectChangeField.NETWORK_AMOUNT, "40")));
        approvals.decide(leader, request.id(), true, null);
        assertThat(projects.findDetail(reporter, project).contractNo()).isNull();
        assertThat(projects.findDetail(reporter, project).signedAmount()).isNull();
        assertThat(projects.findDetail(reporter, project).currentStageCode()).isEqualTo("BIDDING");
        var terminated = create("终止项目");
        forceCollection(terminated, "FIELD-TERMINATE");
        request = approvals.submitMajorChange(reporter, terminated, command(item(ProjectChangeField.STAGE, "TERMINATED"),
                item(ProjectChangeField.TOTAL_AMOUNT, "150")));
        approvals.decide(leader, request.id(), true, null);
        assertThat(projects.findDetail(reporter, terminated).contractNo()).isEqualTo("FIELD-TERMINATE");
        assertThat(projects.findDetail(reporter, terminated).signedAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void categoryOnlyApprovalsAdvanceVersionAtSameInstantAndReplayInWrittenOrder() {
        var project = create("同刻金额变更项目");
        for (var value : List.of("50", "40")) {
            var before = projectRepository.findById(project).orElseThrow().versionNo();
            var request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.NETWORK_AMOUNT, value)));
            approvals.decide(leader, request.id(), true, null);
            assertThat(projectRepository.findById(project).orElseThrow().versionNo()).isEqualTo(before + 1);
        }
        clock.moveTo(7, 1);
        snapshots.generateDue();
        assertThat(snapshots.workbench(leader, YearMonth.of(2026, 6), false).categoryTotals().network())
                .isEqualByComparingTo("40.00");
    }

    @Test
    void lateApprovalRevisesOnlyAffectedProjectAndRecordsCategoryBeforeAndAfter() throws Exception {
        var project = create("跨月变更项目");
        var other = create("未变更项目");
        var request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.TOTAL_AMOUNT, "150"),
                item(ProjectChangeField.NETWORK_AMOUNT, "80"), item(ProjectChangeField.GEWU_AMOUNT, "20")));
        clock.moveTo(7, 1);
        snapshots.generateDue();
        assertThat(snapshots.workbench(leader, YearMonth.of(2026, 6), false).categoryTotals().network())
                .isEqualByComparingTo("120.00");
        clock.moveTo(7, 2);
        approvals.decide(leader, request.id(), true, null);
        var history = snapshots.workbench(leader, YearMonth.of(2026, 6), false);
        assertThat(history.totalExpectedAmount()).isEqualByComparingTo("250.00");
        assertThat(history.categoryTotals().network()).isEqualByComparingTo("140.00");
        assertThat(history.categoryTotals().gewu()).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("SELECT revision_no FROM project_monthly_snapshot WHERE project_id = UUID_TO_BIN(?) AND month_start = '2026-06-01'",
                Integer.class, other.toString())).isZero();
        mvc.perform(get("/api/v1/projects/workbench/history/{id}/revisions", project)
                        .param("month", "2026-06").header("X-NDI-Actor-Id", leader.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].beforeCategories.network").value(60))
                .andExpect(jsonPath("$.data[0].afterCategories.network").value(80))
                .andExpect(jsonPath("$.data[0].afterCategories.gewu").value(20));
    }

    private MajorChangeCommand command(MajorChangeItem... items) {
        return new MajorChangeCommand(MajorChangeType.FIELD_CHANGES, ProjectInitiatorLevel.CITY, leader,
                "客户需求调整", null, null, null, List.of(items));
    }
    private MajorChangeItem item(ProjectChangeField code, String value) { return new MajorChangeItem(code, value); }
    private UUID create(String name) {
        return projects.createProject(reporter, new CreateProjectCommand(province, city, name, "客户", "C25", "客户需求",
                new BigDecimal("100.00"), leader, "经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "已开始交流", "继续跟进", null, true, "独立组合变更测试项目")).id();
    }

    @Test
    void opportunityDateChangesOnlyWhenApprovalTakesEffect() {
        var project = create("项目日期规则");
        var original = projects.findDetail(reporter, project).opportunityDate();
        clock.moveTo(6, 16);
        progressService.append(reporter, project, new com.pluto.ndi.projects.service.AppendProgressCommand("追加进展", "下一步", null));
        var detail = projects.findDetail(reporter, project);
        profileService.update(reporter, project, new com.pluto.ndi.projects.service.UpdateProjectProfileCommand(
                detail.versionNo(), "维护后的客户", detail.industryCode(), detail.customerNeed(), leader,
                detail.cityAccountManagerName(), detail.productSupportExpertNames()));
        assertThat(projects.findDetail(reporter, project).opportunityDate()).isEqualTo(original);
        var request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.TOTAL_AMOUNT, "150")));
        assertThat(projects.findDetail(reporter, project).opportunityDate()).isEqualTo(original);
        clock.moveTo(7, 1);
        approvals.decide(leader, request.id(), true, null);
        var approvedAt = clock.instant();
        assertThat(projects.findDetail(reporter, project).opportunityDate()).isEqualTo(approvedAt);
        clock.moveTo(7, 2);
        request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.TOTAL_AMOUNT, "160")));
        approvals.withdraw(reporter, request.id());
        request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.TOTAL_AMOUNT, "170")));
        approvals.decide(leader, request.id(), false, "不同意");
        assertThat(projects.findDetail(reporter, project).opportunityDate()).isEqualTo(approvedAt);
        request = approvals.submitMajorChange(reporter, project, command(item(ProjectChangeField.TOTAL_AMOUNT, "180")));
        jdbc.update("UPDATE project_project SET version_no=version_no+1 WHERE id=UUID_TO_BIN(?)", project.toString());
        assertThat(approvals.decide(leader, request.id(), true, null).status()).isEqualTo(ApprovalStatus.CONFLICT);
        assertThat(projects.findDetail(reporter, project).opportunityDate()).isEqualTo(approvedAt);
    }
    private void user(UUID id, String role, UUID userCity) {
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled) VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                id.toString(), id.toString(), role, province.toString(), userCity == null ? null : userCity.toString());
        jdbc.update("INSERT INTO identity_user_role VALUES (UUID_TO_BIN(?), ?)", id.toString(), role);
    }
    private BigDecimal category(UUID project, String code) {
        return jdbc.queryForObject("SELECT expected_amount FROM project_category_amount WHERE project_id = UUID_TO_BIN(?) AND category_code = ?",
                BigDecimal.class, project.toString(), code);
    }
    private void forceCollection(UUID project, String contract) {
        jdbc.update("UPDATE project_project SET current_stage_code = 'COLLECTION', contract_no = ?, signed_amount = 80 WHERE id = UUID_TO_BIN(?)",
                contract, project.toString());
    }
}
