package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.service.CreateProjectCommand;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import com.pluto.ndi.projects.service.ProjectApprovalService;
import com.pluto.ndi.projects.service.ProjectMonthlySnapshotService;
import com.pluto.ndi.projects.service.ProjectProfileService;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.projects.service.UpdateProjectProfileCommand;
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

@SpringBootTest(classes = {NdiApplication.class, ProjectMonthlySnapshotIT.FixedClockConfiguration.class})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProjectMonthlySnapshotIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableClock monthlyTestClock() { return new MutableClock(); }
    }

    static final class MutableClock extends Clock {
        private Instant value = at(2026, 6, 28);

        void moveTo(int year, int month, int day) { value = at(year, month, day); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
        private static Instant at(int year, int month, int day) {
            return LocalDate.of(year, month, day).atTime(3, 0)
                    .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        }
    }

    @Autowired private MutableClock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProjectService projects;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectApprovalService approvals;
    @Autowired private ProjectProfileService profiles;
    @Autowired private ProjectMonthlySnapshotService snapshots;
    @Autowired private MockMvc mvc;

    @Test
    void revisesOnlyApprovedProjectAndKeepsIndustryAtItsOwnMonth() throws Exception {
        var provinceId = UUID.randomUUID();
        var cityId = UUID.randomUUID();
        var reporterId = UUID.randomUUID();
        var leaderId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province (id, province_code, province_name, enabled) VALUES (UUID_TO_BIN(?), ?, '快照测试省', true)",
                provinceId.toString(), provinceId.toString().replace("-", ""));
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '快照测试市', true)",
                cityId.toString(), provinceId.toString(), cityId.toString().replace("-", ""));
        addUser(reporterId, provinceId, cityId, "CITY_REPORTER");
        addUser(leaderId, provinceId, null, "PROVINCE_REPORTER");
        var projectId = create(provinceId, cityId, leaderId, reporterId, "跨月项目");
        var otherId = create(provinceId, cityId, leaderId, reporterId, "未变项目");
        jdbc.update("DELETE FROM project_monthly_state_event WHERE project_id = UUID_TO_BIN(?)",
                otherId.toString());
        snapshots.initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_monthly_state_event WHERE project_id = UUID_TO_BIN(?) AND baseline = true",
                Long.class, otherId.toString())).isEqualTo(1);
        var pending = approvals.submitMajorChange(reporterId, projectId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, leaderId,
                "6 月预算核准", null, new BigDecimal("120.00"), null));
        var rejected = approvals.submitMajorChange(reporterId, otherId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, leaderId,
                "未通过的金额申请", null, new BigDecimal("150.00"), null));

        clock.moveTo(2026, 7, 1);
        snapshots.generateDue();
        snapshots.generateDue();
        var nationalReaderId = UUID.randomUUID();
        addUser(nationalReaderId, provinceId, null, "GROUP_REVIEWER");
        assertThat(snapshots.workbench(nationalReaderId, YearMonth.of(2026, 6), false).projectCount())
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_monthly_snapshot WHERE month_start = '2026-06-01'",
                Long.class)).isEqualTo(2);
        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 6), false).totalExpectedAmount())
                .isEqualByComparingTo("200.00");
        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 6), false)
                .categoryTotals().gewu()).isEqualByComparingTo("60.00");
        assertThat(jdbc.queryForObject("SELECT revision_no FROM project_monthly_snapshot WHERE month_start = '2026-06-01' AND project_id = UUID_TO_BIN(?)",
                Integer.class, projectId.toString())).isZero();

        clock.moveTo(2026, 7, 3);
        approvals.decide(leaderId, pending.id(), true, null);
        var june = snapshots.workbench(leaderId, YearMonth.of(2026, 6), false);
        assertThat(june.totalExpectedAmount()).isEqualByComparingTo("220.00");
        assertThat(june.categoryTotals().gewu()).isEqualByComparingTo("60.00");
        assertThat(june.industries()).extracting("industryCode").containsExactly("C25");
        assertThat(jdbc.queryForObject("SELECT revision_no FROM project_monthly_snapshot WHERE month_start = '2026-06-01' AND project_id = UUID_TO_BIN(?)",
                Integer.class, projectId.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revision_no FROM project_monthly_snapshot WHERE month_start = '2026-06-01' AND project_id = UUID_TO_BIN(?)",
                Integer.class, otherId.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM project_monthly_snapshot_revision WHERE source_id = UUID_TO_BIN(?)",
                String.class, pending.id().toString())).isEqualTo("6 月预算核准");
        assertThat(snapshots.revisions(leaderId, projectId, YearMonth.of(2026, 6)))
                .extracting("reason").containsExactly("6 月预算核准");

        approvals.decide(leaderId, rejected.id(), false, "不同意");
        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 6), false).totalExpectedAmount())
                .isEqualByComparingTo("220.00");

        clock.moveTo(2026, 7, 28);
        var late = approvals.submitMajorChange(reporterId, otherId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, leaderId,
                "7 月预算追加", null, new BigDecimal("130.00"), null));

        clock.moveTo(2026, 8, 1);
        snapshots.generateDue();
        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 7), false).totalExpectedAmount())
                .isEqualByComparingTo("220.00");
        clock.moveTo(2026, 8, 10);
        var version = projectRepository.findById(projectId).orElseThrow().versionNo();
        profiles.update(reporterId, projectId, new UpdateProjectProfileCommand(version,
                "客户", "C26", "制造需求", leaderId, "客户经理"));
        clock.moveTo(2026, 9, 1);
        snapshots.generateDue();
        clock.moveTo(2026, 9, 3);
        approvals.decide(leaderId, late.id(), true, null);

        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 6), false).industries())
                .extracting("industryCode").containsExactly("C25");
        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 8), false).industries())
                .extracting("industryCode").containsExactlyInAnyOrder("C25", "C26");
        assertThat(snapshots.workbench(leaderId, YearMonth.of(2026, 8), false)
                .categoryTotals().gewu()).isEqualByComparingTo("60.00");
        assertThat(snapshots.trend(leaderId, YearMonth.of(2026, 6), YearMonth.of(2026, 8), false))
                .hasSize(3).extracting("totalExpectedAmount")
                .containsExactly(new BigDecimal("220.00"), new BigDecimal("250.00"), new BigDecimal("250.00"));
        var stageTrend = snapshots.stageTrend(leaderId, YearMonth.of(2026, 6),
                YearMonth.of(2026, 8), false,
                List.of(ProjectStage.SOLUTION_EXCHANGE, ProjectStage.BIDDING));
        assertThat(stageTrend).hasSize(6);
        assertThat(stageTrend).filteredOn(item -> item.stageCode().equals("SOLUTION_EXCHANGE"))
                .extracting("totalExpectedAmount")
                .containsExactly(new BigDecimal("220.00"), new BigDecimal("250.00"),
                        new BigDecimal("250.00"));
        assertThat(stageTrend).filteredOn(item -> item.stageCode().equals("BIDDING"))
                .allSatisfy(item -> assertThat(item.projectCount()).isZero());
        assertThat(stageTrend).filteredOn(item -> item.stageCode().equals("SOLUTION_EXCHANGE"))
                .allSatisfy(item -> assertThat(item.categoryTotals().gewu()).isEqualByComparingTo("60.00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_monthly_snapshot_revision WHERE source_id = UUID_TO_BIN(?)",
                Long.class, late.id().toString())).isEqualTo(2);
        mvc.perform(get("/api/v1/projects/workbench/history")
                        .param("month", "2026-06").header("X-NDI-Actor-Id", leaderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalExpectedAmount").value(220.00))
                .andExpect(jsonPath("$.data.categoryTotals.gewu").value(60.00));
        mvc.perform(get("/api/v1/projects/workbench/trend")
                        .param("from", "2026-06").param("to", "2026-08")
                        .header("X-NDI-Actor-Id", leaderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get("/api/v1/projects/workbench/stage-trend")
                        .param("from", "2026-06").param("to", "2026-08")
                        .param("stage", "SOLUTION_EXCHANGE", "BIDDING")
                        .header("X-NDI-Actor-Id", leaderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].categoryTotals.gewu").value(60.00));
        mvc.perform(get("/api/v1/projects/workbench/stage-trend")
                        .param("from", "2026-06").param("to", "2026-08")
                        .param("stage", "TERMINATED")
                        .header("X-NDI-Actor-Id", leaderId.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/workbench/history/{projectId}/revisions", projectId)
                        .param("month", "2026-06").header("X-NDI-Actor-Id", leaderId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reason").value("6 月预算核准"));
        mvc.perform(get("/api/v1/projects/workbench/history")
                        .param("month", "2026-09").header("X-NDI-Actor-Id", leaderId.toString()))
                .andExpect(status().isNotFound());

        var otherCityId = UUID.randomUUID();
        var otherReporterId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '快照其他市', true)",
                otherCityId.toString(), provinceId.toString(), otherCityId.toString().replace("-", ""));
        addUser(otherReporterId, provinceId, otherCityId, "CITY_REPORTER");
        clock.moveTo(2026, 9, 28);
        create(provinceId, otherCityId, leaderId, otherReporterId, "其他地市项目");
        clock.moveTo(2026, 10, 1);
        snapshots.generateDue();
        assertThat(snapshots.stageTrend(otherReporterId, YearMonth.of(2026, 9),
                YearMonth.of(2026, 9), false, List.of(ProjectStage.SOLUTION_EXCHANGE)))
                .singleElement().satisfies(item -> {
                    assertThat(item.projectCount()).isEqualTo(1);
                    assertThat(item.categoryTotals().gewu()).isEqualByComparingTo("30.00");
                });
        assertThat(snapshots.stageTrend(leaderId, YearMonth.of(2026, 9),
                YearMonth.of(2026, 9), false, List.of(ProjectStage.SOLUTION_EXCHANGE)))
                .singleElement().satisfies(item -> assertThat(item.projectCount()).isEqualTo(3));
    }

    private UUID create(UUID provinceId, UUID cityId, UUID leaderId, UUID reporterId, String name) {
        return projects.createProject(reporterId, new CreateProjectCommand(provinceId, cityId,
                name, "客户", "C25", "制造需求", new BigDecimal("100.00"), leaderId,
                "客户经理", List.of(new CreateProjectCommand.CategoryAmount("GEWU", new BigDecimal("30.00"))),
                "已交流", "下一步跟进")).id();
    }

    private void addUser(UUID id, UUID provinceId, UUID cityId, String role) {
        jdbc.update("""
                INSERT INTO identity_user_account
                    (id, login_name, display_name, province_id, city_id, enabled)
                VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), true)
                """, id.toString(), id.toString(), role, provinceId.toString(),
                cityId == null ? null : cityId.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), ?)",
                id.toString(), role);
    }
}
