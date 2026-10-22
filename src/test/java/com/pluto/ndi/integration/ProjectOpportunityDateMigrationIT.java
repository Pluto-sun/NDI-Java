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

/** 验证升级时重建项目日期，且不把旧库迁移时刻当成旧项目日期。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectOpportunityDateMigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void reconstructsKnownDatesButLeavesUnrecoverableLegacyDatesUnknown() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("30")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var province = UUID.randomUUID().toString(); var city = UUID.randomUUID().toString(); var actor = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,'日期省',true)", province, province.replace("-", ""));
        jdbc.update("INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'日期市',true)", city, province, city.replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account(id,login_name,display_name,enabled) VALUES(UUID_TO_BIN(?),?,'日期账号',true)", actor, actor);
        var manual = project(jdbc, province, city, actor);
        var legacy = project(jdbc, province, city, actor);
        var imported = project(jdbc, province, city, actor);
        var legacyWithoutLink = project(jdbc, province, city, actor);
        event(jdbc, manual, actor, "PROJECT_CREATED", "{}", "2026-01-01 00:00:00");
        event(jdbc, manual, actor, "MAJOR_CHANGE_APPROVED", "{}", "2026-02-01 00:00:00");
        event(jdbc, manual, actor, "PROJECT_PROFILE_UPDATED", "{}", "2026-03-01 00:00:00");
        event(jdbc, imported, actor, "PROJECT_CREATED", "{\"source\":\"EXCEL_IMPORT\"}", "2026-04-01 00:00:00");
        event(jdbc, legacy, actor, "PROJECT_CREATED", "{\"source\":\"LEGACY_MYSQL\"}", "2026-05-01 00:00:00");
        event(jdbc, legacyWithoutLink, actor, "PROJECT_CREATED", "{\"source\":\"LEGACY_MYSQL\"}", "2026-05-01 00:00:00");
        jdbc.update("INSERT INTO project_source_record(id,project_id,source_system,source_record_id,source_type,imported_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'LEGACY_MYSQL','OLD-1','ONLINE','2026-05-01')", UUID.randomUUID().toString(), legacy);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        assertThat(date(jdbc, manual)).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
        assertThat(date(jdbc, imported)).isEqualTo(LocalDateTime.of(2026, 4, 1, 0, 0));
        assertThat(date(jdbc, legacy)).isNull();
        assertThat(date(jdbc, legacyWithoutLink)).isNull();
    }

    private String project(JdbcTemplate jdbc, String province, String city, String actor) {
        var id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO project_project(id,project_code,province_id,city_id,project_name,customer_name,
                    industry_code,customer_need,total_amount,current_stage_code,province_leader_user_id,
                    city_account_manager_name,version_no,created_by,created_at,updated_by,updated_at,construction_content_raw)
                VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),'日期项目','客户','C25','需求',100,
                    'SOLUTION_EXCHANGE',UUID_TO_BIN(?),'经理',0,UUID_TO_BIN(?),'2026-01-01',UUID_TO_BIN(?),'2026-03-01','需求')
                """, id, "D-" + id, province, city, actor, actor, actor);
        return id;
    }
    private void event(JdbcTemplate jdbc, String project, String actor, String type, String payload, String at) {
        var version = jdbc.queryForObject("SELECT COUNT(*) FROM project_change_event WHERE project_id=UUID_TO_BIN(?)", Integer.class, project);
        jdbc.update("INSERT INTO project_change_event VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,CAST(? AS JSON),UUID_TO_BIN(?),?)", UUID.randomUUID().toString(), project, type, version, payload, actor, at);
    }
    private LocalDateTime date(JdbcTemplate jdbc, String project) {
        return jdbc.queryForObject("SELECT opportunity_date FROM project_project WHERE id=UUID_TO_BIN(?)", LocalDateTime.class, project);
    }
}
