package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 验证既有项目从 v18 升到 v19 时，建设原文不会丢失。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectNarrativeMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void backfillsExistingProjectBeforeMakingRawContentRequired() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("18")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var province = UUID.randomUUID().toString();
        var city = UUID.randomUUID().toString();
        var actor = UUID.randomUUID().toString();
        var project = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES(UUID_TO_BIN(?),?,?,true)",
                province, province.replace("-", ""), "迁移省");
        jdbc.update("INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city, province, city.replace("-", ""), "迁移市");
        jdbc.update("INSERT INTO identity_user_account(id,login_name,display_name,province_id,city_id,enabled) VALUES(UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),true)",
                actor, actor, "迁移负责人", province, city);
        jdbc.update("""
                INSERT INTO project_project(id,project_code,province_id,city_id,project_name,
                    customer_name,industry_code,customer_need,total_amount,current_stage_code,
                    province_leader_user_id,city_account_manager_name,signing_subject_code,
                    version_no,created_by,created_at,updated_by,updated_at)
                VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,100.00,
                    'SOLUTION_EXCHANGE',UUID_TO_BIN(?),?,'PROVINCE_SIGN',0,
                    UUID_TO_BIN(?),NOW(6),UUID_TO_BIN(?),NOW(6))
                """, project, "MIG-" + project, province, city, "迁移项目", "客户", "C25",
                "旧版客户需求", actor, "客户经理", actor, actor);

        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("""
                SELECT construction_content_raw FROM project_project WHERE id = UUID_TO_BIN(?)
                """, String.class, project)).isEqualTo("旧版客户需求");
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'project_project'
                    AND column_name = 'construction_content_raw'
                """, String.class)).isEqualTo("NO");
    }
}
