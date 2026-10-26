package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/** 以旧业务记录演练升级，校验人员回填和行政区划不改变原主键。 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectPeopleDirectoryMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @Test
    void preservesLegacyPeopleAmountsAndLocationIdentifiers() {
        var source =
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure()
                .dataSource(source)
                .target(MigrationVersion.fromVersion("36"))
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(source);
        String province = UUID.randomUUID().toString(),
                city = UUID.randomUUID().toString(),
                actor = UUID.randomUUID().toString(),
                project = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,'广东省',TRUE)",
                province,
                province.replace("-", ""));
        jdbc.update(
                "INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'广州市',TRUE)",
                city,
                province,
                city.replace("-", ""));
        jdbc.update(
                "INSERT INTO identity_user_account(id,login_name,display_name,province_id,enabled)"
                    + " VALUES(UUID_TO_BIN(?),?,'原省分负责人',UUID_TO_BIN(?),TRUE)",
                actor,
                actor,
                province);
        jdbc.update(
                """
INSERT INTO project_project(id,project_code,province_id,city_id,project_name,customer_name,industry_code,
    customer_need,total_amount,current_stage_code,province_leader_user_id,city_account_manager_name,
    version_no,created_by,created_at,updated_by,updated_at,construction_content_raw)
VALUES(UUID_TO_BIN(?),?,UUID_TO_BIN(?),UUID_TO_BIN(?),'迁移人员项目','客户','C25','需求',123.45,
    'SOLUTION_EXCHANGE',UUID_TO_BIN(?),'原地市经理',0,UUID_TO_BIN(?),NOW(),UUID_TO_BIN(?),NOW(),'需求')
""",
                project,
                "M-" + project,
                province,
                city,
                actor,
                actor,
                actor);
        jdbc.update("INSERT INTO project_support_expert VALUES(UUID_TO_BIN(?),0,'原支撑专家')", project);
        var upgraded = Flyway.configure().dataSource(source).load();
        upgraded.migrate();
        assertThat(
                        jdbc.queryForList(
                                "SELECT person_name FROM project_responsible_person WHERE"
                                    + " project_id=UUID_TO_BIN(?) ORDER BY sort_order",
                                String.class,
                                project))
                .containsExactly("原省分负责人", "原地市经理", "原支撑专家");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT BIN_TO_UUID(id) FROM organization_province WHERE"
                                    + " province_code='440000'",
                                String.class))
                .isEqualTo(province);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT BIN_TO_UUID(id) FROM organization_city WHERE"
                                    + " city_code='440100'",
                                String.class))
                .isEqualTo(city);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT total_amount FROM project_project WHERE id=UUID_TO_BIN(?)",
                                java.math.BigDecimal.class,
                                project))
                .isEqualByComparingTo("123.45");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM organization_administrative_region WHERE"
                                    + " kind='PROVINCE'",
                                Integer.class))
                .isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organization_city", Integer.class))
                .isEqualTo(372);
        upgraded.migrate();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM project_responsible_person", Integer.class))
                .isEqualTo(3);
    }
}
