package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 归属升级按既有阶段事实重建，不能把审批时刻或普通金额修改误当阶段发生月。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectAttributionMigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void reconstructsBusinessMonthsFromCreationAdvanceAndRollbackFacts() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("33")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var province = UUID.randomUUID().toString(); var city = UUID.randomUUID().toString(); var actor = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,'升级省',true)", province, province.replace("-", ""));
        jdbc.update("INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'升级市',true)", city, province, city.replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account(id,login_name,display_name,enabled) VALUES(UUID_TO_BIN(?),?,'负责人',true)", actor, actor);
        var created = project(jdbc, province, city, actor);
        var advanced = project(jdbc, province, city, actor);
        var rolledBack = project(jdbc, province, city, actor);
        var imported = project(jdbc, province, city, actor);
        jdbc.update("UPDATE project_project SET created_at='1990-01-01',opportunity_date='2026-09-01' WHERE id=UUID_TO_BIN(?)", imported);
        jdbc.update("INSERT INTO project_source_record(id,project_id,source_system,source_record_id,source_type,imported_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'EXCEL_IMPORT',?,'EXCEL_IMPORT','2026-07-01')",
                UUID.randomUUID().toString(), imported, imported);
        for (var id : new String[]{advanced, rolledBack}) {
            event(jdbc, id, actor, 1, "STAGE_ADVANCEMENT_APPROVED", "2026-08-02 01:00:00",
                    "{\"stageOccurredAt\":\"2026-06-30T16:00:00Z\"}");
        }
        event(jdbc, advanced, actor, 2, "MAJOR_CHANGE_APPROVED", "2026-09-02 01:00:00",
                "{\"before\":{\"stageCode\":\"BIDDING\"},\"after\":{\"stageCode\":\"BIDDING\"}}");
        event(jdbc, rolledBack, actor, 2, "MAJOR_CHANGE_APPROVED", "2026-09-30 16:00:00",
                "{\"before\":{\"stageCode\":\"BIDDING\"},\"after\":{\"stageCode\":\"SOLUTION_EXCHANGE\"}}");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(month(jdbc, created)).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(month(jdbc, advanced)).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(month(jdbc, rolledBack)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(month(jdbc, imported)).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    private String project(JdbcTemplate jdbc, String province, String city, String actor) {
        var id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO project_project(id,project_code,province_id,city_id,project_name,customer_name,industry_code,
                    customer_need,construction_content_raw,total_amount,current_stage_code,province_leader_user_id,
                    city_account_manager_name,version_no,created_by,created_at,updated_by,updated_at)
                VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),'项目','客户','C25','需求','建设',100,
                    'SOLUTION_EXCHANGE',UUID_TO_BIN(?),'经理',0,UUID_TO_BIN(?),'2026-05-31 16:00:00',UUID_TO_BIN(?),'2026-05-31 16:00:00')
                """, id, id, province, city, actor, actor, actor);
        return id;
    }
    private void event(JdbcTemplate jdbc, String id, String actor, int version, String type, String at, String payload) {
        jdbc.update("INSERT INTO project_change_event VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,CAST(? AS JSON),UUID_TO_BIN(?),?)",
                UUID.randomUUID().toString(), id, type, version, payload, actor, at);
    }
    private LocalDate month(JdbcTemplate jdbc, String project) {
        return jdbc.queryForObject("SELECT attribution_month FROM project_project WHERE id=UUID_TO_BIN(?)", LocalDate.class, project);
    }
}
