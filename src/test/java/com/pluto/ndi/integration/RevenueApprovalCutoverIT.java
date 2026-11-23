package com.pluto.ndi.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RevenueApprovalCutoverIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void withdrawsLegacyPendingRequestsWithoutChangingEffectiveRecognition() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).target("62").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var entry = java.util.UUID.randomUUID().toString();
        var actor = java.util.UUID.randomUUID().toString();
        var approver = java.util.UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO revenue_entry(id,version_no,account_month,specific_organization,contract_no,clause_no,
                    clause_amount,source_amount,source_fields,recognition,recognized_amount,created_by,created_at,updated_at)
                VALUES(UUID_TO_BIN(?),7,'2026-11-01',TRUE,'CUTOVER','1',5,9,JSON_OBJECT(),
                    JSON_OBJECT('amount',30),30,UUID_TO_BIN(?),NOW(6),NOW(6))
                """, entry, actor);
        jdbc.update("""
                INSERT INTO revenue_recognition_request(id,entry_id,initiator_id,approver_id,base_version,status,proposed,created_at)
                VALUES(UUID_TO_BIN(UUID()),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),7,'PENDING',JSON_OBJECT('amount',100),NOW(6))
                """, entry, actor, approver);
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertThat(jdbc.queryForObject("SELECT status FROM revenue_recognition_request", String.class)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT opinion FROM revenue_recognition_request", String.class)).contains("流程切换");
        assertThat(jdbc.queryForObject("SELECT recognized_amount FROM revenue_entry", java.math.BigDecimal.class))
                .isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT version_no FROM revenue_entry", Long.class)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE event_type='REVENUE_WITHDRAWN'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revenue_notification WHERE kind='WITHDRAWN'", Integer.class)).isEqualTo(2);
    }
}
