package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectAttributionFilter;
import com.pluto.ndi.projects.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证唯一归属、审批事务、统计去重和数据范围，不用月末快照代替所属月份。 */
@SpringBootTest(classes = {NdiApplication.class, ProjectMonthlySnapshotIT.FixedClockConfiguration.class})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProjectAttributionIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ProjectService projects;
    @Autowired ProjectStageService stages;
    @Autowired ProjectApprovalService approvals;
    @Autowired ProjectAttributionService statistics;
    @Autowired ProjectDashboardService dashboard;
    @Autowired ProjectMapService maps;
    @Autowired ProjectMonthlySnapshotIT.MutableClock clock;
    @MockitoBean DocumentDirectory documents;

    @Test
    void countsEachProjectOnceAndMovesOnlyAfterStageApproval() throws Exception {
        var province = UUID.randomUUID(); var city = UUID.randomUUID();
        var reporter = UUID.randomUUID(); var leader = UUID.randomUUID(); var reviewer = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,'归属省',true)", province.toString(), province.toString().replace("-", ""));
        jdbc.update("INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'归属市',true)", city.toString(), province.toString(), city.toString().replace("-", ""));
        user(reporter, province, city, "CITY_REPORTER");
        user(leader, province, null, "PROVINCE_REPORTER");
        user(reviewer, province, null, "INTERFACE_REVIEWER");
        jdbc.update("INSERT INTO identity_reviewer_province VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", reviewer.toString(), province.toString());
        clock.moveTo(2026, 6, 28);
        var first = create(province, city, leader, reporter);
        clock.moveTo(2026, 7, 5);
        var second = create(province, city, leader, reporter);
        var otherCity = UUID.randomUUID(); var otherReporter = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'外市',true)",
                otherCity.toString(), province.toString(), otherCity.toString().replace("-", ""));
        user(otherReporter, province, otherCity, "CITY_REPORTER");
        create(province, otherCity, leader, otherReporter);
        when(documents.isUsable(any(), any(), any(), any())).thenReturn(true);
        var evidence = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document_object(id,scope_type,scope_id,purpose_code,bucket_name,staging_object_key,
                    object_key,original_name,media_type,byte_size,sha256_hex,status,uploaded_by,created_at)
                VALUES(UUID_TO_BIN(?),'PROJECT',UUID_TO_BIN(?),'CUSTOMER_SOLUTION','test',?,?,'方案.pdf',
                    'application/pdf',1,?,'AVAILABLE',UUID_TO_BIN(?),NOW())
                """, evidence.toString(), first.toString(), "staging-" + evidence, "object-" + evidence,
                "a".repeat(64), reporter.toString());
        var pending = stages.submit(reporter, first, new StageAdvanceCommand(ProjectInitiatorLevel.CITY,
                ProjectStage.BIDDING, reviewer, LocalDate.of(2026, 7, 5), evidence, null, null, null, null));
        assertThat(view(reporter, "2026-06", "2026-06", false).projectCount()).isEqualTo(1);
        assertThat(view(reporter, "2026-07", "2026-07", false).projectCount()).isEqualTo(1);
        clock.moveTo(2026, 8, 3);
        approvals.decide(reviewer, pending.id(), true, null);
        assertThat(projects.findDetail(reporter, first).attributionMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(view(reporter, "2026-06", "2026-06", false).projectCount()).isZero();
        var combined = view(reporter, "2026-06", "2026-08", false);
        assertThat(combined.projectCount()).isEqualTo(2);
        assertThat(combined.totalExpectedAmount()).isEqualByComparingTo("200");
        assertThat(combined.categoryTotals().network()).isEqualByComparingTo("120");
        assertThat(combined.categoryTotals().gewu()).isEqualByComparingTo("60");
        assertThat(combined.trend().stream().mapToLong(ProjectStageTrendPoint::projectCount).sum()).isEqualTo(2);
        var filter = new ProjectAttributionFilter(YearMonth.of(2026, 6), YearMonth.of(2026, 8), false,
                List.of(ProjectStage.SOLUTION_EXCHANGE, ProjectStage.BIDDING), null, null);
        var map = maps.attribution(reporter, null, filter);
        assertThat(map.attributionFrom()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(map.regions()).filteredOn(region -> region.id().equals(city)).singleElement().satisfies(region -> {
            assertThat(region.projectCount()).isEqualTo(combined.projectCount());
            assertThat(region.expectedAmount()).isEqualByComparingTo(combined.totalExpectedAmount());
            assertThat(region.categoryTotals()).isEqualTo(combined.categoryTotals());
        });
        assertThat(map.regions()).filteredOn(region -> region.id().equals(otherCity)).singleElement().satisfies(region -> {
            assertThat(region.authorized()).isFalse();
            assertThat(region.projectCount()).isZero();
        });
        assertThat(view(leader, "2026-06", "2026-08", false).projectCount()).isEqualTo(3);
        assertThat(maps.attribution(leader, province, new ProjectAttributionFilter(filter.from(), filter.to(), false,
                filter.stages(), List.of(province), List.of(city))).regions())
                .filteredOn(region -> region.id().equals(city)).singleElement().satisfies(region ->
                        assertThat(region.projectCount()).isEqualTo(2));
        mvc.perform(get("/api/v1/projects/workbench/map").header("X-NDI-Actor-Id", reporter.toString())
                        .param("from", "2026-06").param("to", "2026-08").param("stage", "SOLUTION_EXCHANGE", "BIDDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.attributionFrom").value("2026-06"))
                .andExpect(jsonPath("$.data.attributionTo").value("2026-08"));
        mvc.perform(get("/api/v1/projects/workbench/map").header("X-NDI-Actor-Id", reporter.toString())
                        .param("from", "2026-06").param("to", "2026-08").param("month", "2026-06"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/workbench/map").header("X-NDI-Actor-Id", reporter.toString())
                        .param("from", "2026-06"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/workbench/map").header("X-NDI-Actor-Id", reporter.toString())
                        .param("from", "2026-06").param("to", "2026-08").param("provinceId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        assertThat(view(reporter, "2026-08", "2026-08", false).projectCount()).isZero();
        var rejected = approvals.submitMajorChange(reporter, second, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, leader, "不通过的终止", null, null, null));
        approvals.decide(leader, rejected.id(), false, "继续跟进");
        var withdrawn = approvals.submitMajorChange(reporter, second, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, leader, "撤销终止", null, null, null));
        approvals.withdraw(reporter, withdrawn.id());
        assertThat(projects.findDetail(reporter, second).attributionMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
        clock.moveTo(2026, 9, 2);
        var amount = approvals.submitMajorChange(reporter, first, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, leader, "金额核准", null, new BigDecimal("120"), null));
        approvals.decide(leader, amount.id(), true, null);
        assertThat(view(reporter, "2026-07", "2026-07", false).totalExpectedAmount()).isEqualByComparingTo("220");
        clock.moveTo(2026, 10, 1);
        var rollback = approvals.submitMajorChange(reporter, first, new MajorChangeCommand(
                MajorChangeType.STAGE_ROLLBACK, ProjectInitiatorLevel.CITY, leader, "回退重新交流", null, null, ProjectStage.SOLUTION_EXCHANGE));
        approvals.decide(leader, rollback.id(), true, null);
        assertThat(projects.findDetail(reporter, first).attributionMonth()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(view(reporter, "2026-07", "2026-07", false).projectCount()).isEqualTo(1);
        var terminate = approvals.submitMajorChange(reporter, first, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, leader, "业务终止", null, null, null));
        approvals.decide(leader, terminate.id(), true, null);
        assertThat(view(reporter, "2026-06", "2026-10", false).projectCount()).isEqualTo(1);
        assertThat(view(reporter, "2026-06", "2026-10", true).projectCount()).isEqualTo(2);
        mvc.perform(get("/api/v1/projects/workbench/attribution").header("X-NDI-Actor-Id", reporter.toString())
                        .param("from", "2026-06").param("to", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectCount").value(1));
        mvc.perform(get("/api/v1/projects/workbench/attribution").header("X-NDI-Actor-Id", reporter.toString())
                        .param("from", "2026-06").param("to", "2026-10").param("provinceIds", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectCount").value(0));
        for (var from : List.of("2026-11", "2020-01", "invalid")) {
            mvc.perform(get("/api/v1/projects/workbench/attribution").header("X-NDI-Actor-Id", reporter.toString())
                            .param("from", from).param("to", "2026-10"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void dashboardCountsZeroAmountMembershipDeduplicatesDigitalAndFiltersAllSections() throws Exception {
        var province = UUID.randomUUID(); var city = UUID.randomUUID(); var otherCity = UUID.randomUUID();
        var reporter = UUID.randomUUID(); var other = UUID.randomUUID(); var leader = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,'广东省',true)", province.toString(), province.toString().replace("-", ""));
        for (var id : List.of(city, otherCity)) jdbc.update("INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'广州市',true)", id.toString(), province.toString(), id.toString().replace("-", ""));
        user(reporter, province, city, "CITY_REPORTER"); user(other, province, otherCity, "CITY_REPORTER");
        user(leader, province, null, "PROVINCE_REPORTER");
        clock.moveTo(2026, 10, 8);
        var first = create(province, city, leader, reporter);
        create(province, otherCity, leader, other);
        jdbc.update("INSERT INTO project_category_amount VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'BIG_DATA',0)", UUID.randomUUID().toString(), first.toString());
        var filter = new com.pluto.ndi.projects.domain.ProjectDashboardFilter(YearMonth.of(2026, 10), YearMonth.of(2026, 10), null, "", "", false);
        var result = dashboard.view(reporter, filter);
        assertThat(result.current().projectCount()).isEqualTo(1);
        assertThat(result.current().digital().count()).isEqualTo(1);
        assertThat(result.current().gewu().count()).isEqualTo(1);
        assertThat(result.current().bigData().count()).isEqualTo(1);
        assertThat(result.current().bigData().amount()).isEqualByComparingTo("0");
        assertThat(result.current().totalAmount()).isEqualByComparingTo("100");
        assertThat(result.previous().projectCount()).isZero();
        assertThat(result.provinces()).hasSize(1);
        assertThat(result.trend().getFirst().summary()).isEqualTo(result.current());
        assertThat(dashboard.view(leader, filter).current().projectCount()).isEqualTo(2);
        assertThat(dashboard.activities(reporter, filter)).hasSize(1).allSatisfy(activity -> {
            assertThat(activity.projectId()).isEqualTo(first);
            assertThat(activity.beforeAmount()).isNull();
            assertThat(activity.afterAmount()).isEqualByComparingTo("100");
        });
        assertThat(dashboard.view(reporter, new com.pluto.ndi.projects.domain.ProjectDashboardFilter(filter.from(), filter.to(), null, "DIGITAL", "BIG_DATA", false)).current().projectCount()).isEqualTo(1);
        assertThat(dashboard.view(reporter, new com.pluto.ndi.projects.domain.ProjectDashboardFilter(filter.from(), filter.to(), ProjectStage.BIDDING, "", "", false)).current().projectCount()).isZero();
        assertThat(dashboard.activities(reporter, new com.pluto.ndi.projects.domain.ProjectDashboardFilter(filter.from(), filter.to(), ProjectStage.BIDDING, "", "", false))).isEmpty();
        mvc.perform(get("/api/v1/projects/workbench/dashboard").header("X-NDI-Actor-Id", reporter.toString()).param("from", "2026-10").param("to", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.current.bigData.count").value(1));
        mvc.perform(get("/api/v1/projects/workbench/dashboard/activities").header("X-NDI-Actor-Id", reporter.toString()).param("from", "2026-10").param("to", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].projectId").value(first.toString()));
        mvc.perform(get("/api/v1/projects/workbench/dashboard").header("X-NDI-Actor-Id", reporter.toString()).param("from", "2026-10").param("to", "2026-10").param("product", "invalid"))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE project_project SET current_stage_code='TERMINATED' WHERE id=UUID_TO_BIN(?)", first.toString());
        assertThat(dashboard.view(reporter, filter).current().projectCount()).isZero();
        assertThat(dashboard.activities(reporter, filter)).isEmpty();
        assertThat(dashboard.view(reporter, new com.pluto.ndi.projects.domain.ProjectDashboardFilter(filter.from(), filter.to(), null, "", "", true)).current().projectCount()).isEqualTo(1);
    }

    private ProjectAttributionWorkbench view(UUID actor, String from, String to, boolean terminated) {
        return statistics.view(actor, YearMonth.parse(from), YearMonth.parse(to), terminated, null, null, null);
    }
    private UUID create(UUID province, UUID city, UUID leader, UUID reporter) {
        return projects.createProject(reporter, new CreateProjectCommand(province, city, "归属-" + UUID.randomUUID(),
                "客户", "C25", "需求", new BigDecimal("100"), leader, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60")),
                        new CreateProjectCommand.CategoryAmount("GEWU", new BigDecimal("30"))),
                "首次交流", "继续交流", null, true, "独立验收项目")).id();
    }
    private void user(UUID id, UUID province, UUID city, String role) {
        jdbc.update("INSERT INTO identity_user_account(id,login_name,display_name,province_id,city_id,enabled) VALUES(UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),true)",
                id.toString(), id.toString(), role, province.toString(), city == null ? null : city.toString());
        jdbc.update("INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),?)", id.toString(), role);
    }
}
