package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证已有 V21 历史快照升级后仍保留分类金额。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectCategorySnapshotMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void backfillsExistingBaselineAndCompletedSnapshot() {
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource)
                .target(MigrationVersion.fromVersion("21")).load().migrate();

        var province = UUID.randomUUID();
        var city = UUID.randomUUID();
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province VALUES (UUID_TO_BIN(?), ?, ?, true)",
                province.toString(), "PX", "迁移省");
        jdbc.update("INSERT INTO organization_city VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, true)",
                city.toString(), province.toString(), "CX", "迁移市");
        jdbc.update("INSERT INTO identity_user_account VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                user.toString(), "migration-user", "迁移用户", province.toString(), city.toString());
        jdbc.update("""
                INSERT INTO project_project
                    (id, project_code, province_id, city_id, project_name, customer_name,
                     industry_code, customer_need, construction_content_raw, total_amount,
                     current_stage_code, province_leader_user_id, city_account_manager_name,
                     version_no, created_by, created_at, updated_by, updated_at)
                VALUES (UUID_TO_BIN(?), 'MIGRATION-001', UUID_TO_BIN(?), UUID_TO_BIN(?),
                        '迁移项目', '迁移客户', 'C25', '需求', '需求', 100,
                        'SOLUTION_EXCHANGE', UUID_TO_BIN(?), '客户经理', 0,
                        UUID_TO_BIN(?), NOW(6), UUID_TO_BIN(?), NOW(6))
                """, project.toString(), province.toString(), city.toString(), user.toString(),
                user.toString(), user.toString());
        jdbc.update("INSERT INTO project_category_amount VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'GEWU', 30)",
                UUID.randomUUID().toString(), project.toString());
        jdbc.update("INSERT INTO project_category_amount VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'NETWORK', 20)",
                UUID.randomUUID().toString(), project.toString());
        jdbc.update("""
                INSERT INTO project_monthly_state_event
                    (id, project_id, effective_month, recorded_at, source_kind, source_id, reason,
                     baseline, province_id, city_id, industry_code, stage_code, total_amount)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), '2026-06-01', NOW(6), 'BASELINE',
                        UUID_TO_BIN(?), '原始状态', true, UUID_TO_BIN(?), UUID_TO_BIN(?),
                        'C25', 'SOLUTION_EXCHANGE', 100)
                """, UUID.randomUUID().toString(), project.toString(), project.toString(),
                province.toString(), city.toString());
        jdbc.update("""
                INSERT INTO project_monthly_snapshot
                    (month_start, project_id, province_id, city_id, city_name, industry_code,
                     industry_name, stage_code, total_amount, revision_no, updated_at)
                VALUES ('2026-06-01', UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?),
                        '迁移市', 'C25', '行业', 'SOLUTION_EXCHANGE', 100, 0, NOW(6))
                """, project.toString(), province.toString(), city.toString());

        Flyway.configure().dataSource(dataSource).load().migrate();
        assertThat(jdbc.queryForObject("""
                SELECT gewu_amount FROM project_monthly_state_event
                WHERE project_id = UUID_TO_BIN(?) AND baseline = true
                """, BigDecimal.class, project.toString())).isEqualByComparingTo("30.00");
        assertThat(jdbc.queryForObject("""
                SELECT network_amount FROM project_monthly_snapshot
                WHERE project_id = UUID_TO_BIN(?) AND month_start = '2026-06-01'
                """, BigDecimal.class, project.toString())).isEqualByComparingTo("20.00");
    }
}
