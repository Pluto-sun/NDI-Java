package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.util.UUID;

/** 空标识归一保留审核数据，冲突时原子停止，不自动合并财务明细。 */
@Testcontainers(disabledWithoutDocker = true)
class RevenueEmptyIdentityMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    DriverManagerDataSource datasource;
    JdbcTemplate jdbc;

    @BeforeEach
    void oldDatabase() {
        datasource =
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var flyway =
                Flyway.configure().dataSource(datasource).cleanDisabled(false).target("47").load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(datasource);
    }

    String entry(String contract, String clause) {
        var id = UUID.randomUUID().toString();
        jdbc.update(
                """
INSERT INTO revenue_entry(id,version_no,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,recognition,recognized_amount,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(?),7,'2026-11-01',FALSE,?,?,100,10,JSON_OBJECT('合同编号',?,'条款编号',?,'业务号码/项目编号','--','自然客户名称','--','产品编码','--'),JSON_OBJECT('amount',5),5,UUID_TO_BIN(?),'2026-07-20','2026-07-21')
""",
                id,
                contract,
                clause,
                contract,
                clause,
                UUID.randomUUID().toString());
        return id;
    }

    @Test
    void normalizesAllIdentifiersWithoutChangingRecognitionVersionOrPendingRequest()
            throws Exception {
        var id = entry("--", "--");
        var request = UUID.randomUUID().toString();
        jdbc.update(
                """
INSERT INTO revenue_recognition_request(id,entry_id,initiator_id,approver_id,base_version,status,proposed,created_at)
VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),7,'PENDING',JSON_OBJECT('amount',6),NOW(6))
""",
                request,
                id,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        Flyway.configure().dataSource(datasource).load().migrate();
        assertThat(jdbc.queryForObject("SELECT contract_no FROM revenue_entry", String.class))
                .isEmpty();
        assertThat(jdbc.queryForObject("SELECT clause_no FROM revenue_entry", String.class))
                .isEmpty();
        var source =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(
                                jdbc.queryForObject(
                                        "SELECT source_fields FROM revenue_entry", String.class));
        for (var field : new String[] {"合同编号", "条款编号", "业务号码/项目编号", "自然客户名称", "产品编码"})
            assertThat(source.get(field).asText()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT version_no FROM revenue_entry", Long.class))
                .isEqualTo(7);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT recognized_amount FROM revenue_entry", Integer.class))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT updated_at=TIMESTAMP('2026-07-21') FROM revenue_entry",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM revenue_recognition_request", String.class))
                .isEqualTo("PENDING");
        assertThatThrownBy(() -> entry("--", "--"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> entry("", "")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conflictingPlaceholdersStopBeforeAnyOriginalRowsOrIndexAreRewritten() {
        entry("--", "--");
        entry("", "");
        assertThatThrownBy(() -> Flyway.configure().dataSource(datasource).load().migrate())
                .isInstanceOf(FlywayException.class);
        assertThat(jdbc.queryForList("SELECT clause_no FROM revenue_entry", String.class))
                .containsExactlyInAnyOrder("--", "");
        assertThat(jdbc.queryForList("SELECT contract_no FROM revenue_entry", String.class))
                .containsExactlyInAnyOrder("--", "");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE"
                                    + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='revenue_entry' AND"
                                    + " COLUMN_NAME='contract_number_key'",
                                Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revenue_entry", Integer.class))
                .isEqualTo(2);
    }
}
