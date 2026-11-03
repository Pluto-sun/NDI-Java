package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.util.UUID;

/** 从三字段旧约束升级，历史主档、认定和版本均保持原值。 */
@Testcontainers(disabledWithoutDocker = true)
class RevenueBusinessKeyMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @Test
    void upgradesExistingEntriesAndBackfillsMissingBusinessNumbers() {
        var datasource =
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(datasource).target("43").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        var id = UUID.randomUUID().toString();
        jdbc.update(
                """
INSERT INTO revenue_entry(id,version_no,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,recognition,recognized_amount,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(?),7,'2026-11-01',TRUE,'MIGRATION','001',100,10,JSON_OBJECT('业务号码/项目编号','00001'),JSON_OBJECT('amount',5),5,UUID_TO_BIN(?),NOW(6),NOW(6))
""",
                id,
                UUID.randomUUID().toString());
        jdbc.update(
                """
INSERT INTO revenue_entry(id,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(?),'2026-11-01',TRUE,'MISSING','001',100,10,JSON_OBJECT(),UUID_TO_BIN(?),NOW(6),NOW(6))
""",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        Flyway.configure().dataSource(datasource).load().migrate();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT version_no FROM revenue_entry WHERE id=UUID_TO_BIN(?)",
                                Long.class,
                                id))
                .isEqualTo(7);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT recognized_amount FROM revenue_entry WHERE"
                                    + " id=UUID_TO_BIN(?)",
                                Integer.class,
                                id))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT business_number_hash=UNHEX(SHA2('00001',256)) FROM"
                                    + " revenue_entry WHERE id=UUID_TO_BIN(?)",
                                Boolean.class,
                                id))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT business_number_hash=UNHEX(SHA2('',256)) FROM revenue_entry"
                                    + " WHERE contract_no='MISSING'",
                                Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revenue_entry", Integer.class))
                .isEqualTo(2);
        jdbc.update(
                """
INSERT INTO revenue_entry(id,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
SELECT UUID_TO_BIN(?),account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,JSON_OBJECT('业务号码/项目编号','00002'),created_by,created_at,updated_at FROM revenue_entry WHERE id=UUID_TO_BIN(?)
""",
                UUID.randomUUID().toString(),
                id);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
INSERT INTO revenue_entry(id,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
SELECT UUID_TO_BIN(?),account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,JSON_OBJECT('业务号码/项目编号','00001'),created_by,created_at,updated_at FROM revenue_entry WHERE id=UUID_TO_BIN(?)
""",
                                        UUID.randomUUID().toString(),
                                        id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
