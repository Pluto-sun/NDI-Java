package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 既有库升级采用最近正式变更或进展时间，且不改审批版本。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectActivityMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void backfillsLatestActivityWithoutChangingProjectVersionOrExistingProgress() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("23")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var province = UUID.randomUUID().toString();
        var city = UUID.randomUUID().toString();
        var actor = UUID.randomUUID().toString();
        var project = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organization_province VALUES (UUID_TO_BIN(?), 'ACTIVITY-P', '活动迁移省', true)", province);
        jdbc.update("INSERT INTO organization_city VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'ACTIVITY-C', '活动迁移市', true)", city, province);
        jdbc.update("INSERT INTO identity_user_account VALUES (UUID_TO_BIN(?), 'activity-migration', '迁移账号', UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                actor, province, city);
        jdbc.update("""
                INSERT INTO project_project (id, project_code, province_id, city_id, project_name, customer_name,
                    industry_code, customer_need, construction_content_raw, total_amount, current_stage_code,
                    province_leader_user_id, city_account_manager_name, version_no, created_by, created_at, updated_by, updated_at)
                VALUES (UUID_TO_BIN(?), 'ACTIVITY-MIGRATION', UUID_TO_BIN(?), UUID_TO_BIN(?), '迁移项目', '客户',
                    'C25', '需求', '需求', 100, 'SOLUTION_EXCHANGE', UUID_TO_BIN(?), '经理', 7,
                    UUID_TO_BIN(?), '2026-06-01 00:00:00', UUID_TO_BIN(?), '2026-06-10 00:00:00')
                """, project, province, city, actor, actor, actor);
        jdbc.update("""
                INSERT INTO project_progress (id, project_id, progress_content, next_action, actor_id, occurred_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), '后续进展', '继续跟进', UUID_TO_BIN(?), '2026-06-20 00:00:00')
                """, UUID.randomUUID().toString(), project, actor);

        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT last_activity_at FROM project_activity_state WHERE project_id = UUID_TO_BIN(?)",
                LocalDateTime.class, project)).isEqualTo(LocalDateTime.of(2026, 6, 20, 0, 0));
        assertThat(jdbc.queryForObject("SELECT version_no FROM project_project WHERE id = UUID_TO_BIN(?)",
                Long.class, project)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_progress WHERE project_id = UUID_TO_BIN(?)",
                Long.class, project)).isEqualTo(1);
    }
}
