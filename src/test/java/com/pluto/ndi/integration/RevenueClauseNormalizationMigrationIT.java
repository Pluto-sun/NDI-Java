package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.revenue.util.RevenueSourceNormalizer;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.util.List;
import java.util.UUID;

/** 历史归一化保留认定和待审记录，冲突时在写入前停止，不自动合并财务明细。 */
@Testcontainers(disabledWithoutDocker = true)
class RevenueClauseNormalizationMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    DriverManagerDataSource source;
    JdbcTemplate jdbc;

    @BeforeEach
    void oldDatabase() {
        source =
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var flyway = Flyway.configure().dataSource(source).cleanDisabled(false).target("44").load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(source);
    }

    String entry(String contract, String clause) {
        var id = UUID.randomUUID().toString();
        jdbc.update(
                """
INSERT INTO revenue_entry(id,version_no,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,recognition,recognized_amount,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(?),7,'2026-11-01',TRUE,?,?,100,10,JSON_OBJECT('条款编号',?,'业务号码/项目编号','0001'),JSON_OBJECT('amount',5),5,UUID_TO_BIN(?),'2026-07-20 00:00:00','2026-07-21 00:00:00')
""",
                id,
                contract,
                clause,
                clause,
                UUID.randomUUID().toString());
        return id;
    }

    @Test
    void canonicalizesHistoricalSourceAndGuardsEquivalentDirectSqlWithoutChangingApprovalData()
            throws Exception {
        var id = entry("LEGACY", "002");
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
        var examples =
                List.of("000", "--", "A002", "2-01", "2.01", "0".repeat(150) + "9".repeat(50));
        for (int i = 0; i < examples.size(); i++) entry("EXAMPLE-" + i, examples.get(i));
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT clause_no FROM revenue_entry WHERE id=UUID_TO_BIN(?)",
                                String.class,
                                id))
                .isEqualTo("2");
        var sourceJson =
                jdbc.queryForObject(
                        "SELECT source_fields FROM revenue_entry WHERE id=UUID_TO_BIN(?)",
                        String.class,
                        id);
        assertThat(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(sourceJson)
                                .get("条款编号")
                                .asText())
                .isEqualTo("2");
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
                                        "SELECT updated_at FROM revenue_entry WHERE"
                                                + " id=UUID_TO_BIN(?)",
                                        java.sql.Timestamp.class,
                                        id)
                                .toLocalDateTime()
                                .toString())
                .isEqualTo("2026-07-21T00:00");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM revenue_recognition_request WHERE"
                                        + " id=UUID_TO_BIN(?)",
                                String.class,
                                request))
                .isEqualTo("PENDING");
        for (int i = 0; i < examples.size(); i++)
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT clause_number_key FROM revenue_entry WHERE"
                                            + " contract_no=?",
                                    String.class,
                                    "EXAMPLE-" + i))
                    .isEqualTo(RevenueSourceNormalizer.clauseNumber(examples.get(i)));
        assertThatThrownBy(() -> entry("LEGACY", "0002"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesConflictingLegacyRowsWithoutDeletingOrRewritingEitherEntry() {
        entry("COLLISION", "002");
        entry("COLLISION", "2");
        assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
                .isInstanceOf(FlywayException.class);
        assertThat(
                        jdbc.queryForList(
                                "SELECT clause_no FROM revenue_entry WHERE contract_no='COLLISION'",
                                String.class))
                .containsExactlyInAnyOrder("002", "2");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE"
                                    + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='revenue_entry' AND"
                                    + " COLUMN_NAME='clause_number_key'",
                                Integer.class))
                .isZero();
    }
}
