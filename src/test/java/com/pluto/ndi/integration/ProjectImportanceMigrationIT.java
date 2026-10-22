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

/** 验证既有 Java 项目升级后保留数据并获得默认重要性。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectImportanceMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void defaultsExistingProjectsWhenUpgradingFromVersion32() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("32")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var province = UUID.randomUUID().toString();
        var city = UUID.randomUUID().toString();
        var actor = UUID.randomUUID().toString();
        var project = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,'升级省',true)", province, province.replace("-", ""));
        jdbc.update("INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'升级市',true)", city, province, city.replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account(id,login_name,display_name,enabled) VALUES(UUID_TO_BIN(?),?,'负责人',true)", actor, actor);
        jdbc.update("""
                INSERT INTO project_project(id,project_code,province_id,city_id,project_name,customer_name,
                    industry_code,customer_need,total_amount,current_stage_code,province_leader_user_id,
                    city_account_manager_name,version_no,created_by,created_at,updated_by,updated_at,construction_content_raw)
                VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),'升级项目','客户','C25','需求',123.45,
                    'SOLUTION_EXCHANGE',UUID_TO_BIN(?),'经理',0,UUID_TO_BIN(?),NOW(),UUID_TO_BIN(?),NOW(),'需求')
                """, project, "M-" + project, province, city, actor, actor, actor);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT importance_code FROM project_project WHERE id=UUID_TO_BIN(?)", String.class, project))
                .isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM project_project WHERE id=UUID_TO_BIN(?)", java.math.BigDecimal.class, project))
                .isEqualByComparingTo("123.45");
    }
}
