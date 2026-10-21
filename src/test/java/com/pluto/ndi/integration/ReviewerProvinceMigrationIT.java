package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class ReviewerProvinceMigrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test void migratesOnlyEnabledSingleProvinceReviewersWithoutChangingAccountsOrCredentials() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target(MigrationVersion.fromVersion("27")).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var province = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organization_province (id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),'MIGRATION-SCOPE','迁移省',true)", province);
        var enabled = account(jdbc, province, true, "INTERFACE_REVIEWER");
        var disabled = account(jdbc, province, false, "INTERFACE_REVIEWER");
        var administrator = account(jdbc, province, true, "INTERFACE_REVIEWER");
        jdbc.update("INSERT INTO identity_user_role (user_id,role_code) VALUES (UUID_TO_BIN(?),'SYSTEM_ADMIN')", administrator);
        account(jdbc, province, true, "PROVINCE_REPORTER");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("SELECT BIN_TO_UUID(reviewer_id) FROM identity_reviewer_province", String.class)).containsExactly(enabled);
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(province_id) FROM identity_reviewer_province WHERE reviewer_id=UUID_TO_BIN(?)", String.class, enabled)).isEqualTo(province);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_user_account WHERE credential_version=7 AND management_version=3 AND password_hash IS NULL", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT enabled FROM identity_user_account WHERE id=UUID_TO_BIN(?)", Boolean.class, disabled)).isFalse();
    }
    private String account(JdbcTemplate jdbc, String province, boolean enabled, String role) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,province_id,enabled,credential_version,management_version) VALUES (UUID_TO_BIN(?),?,'迁移账号',UUID_TO_BIN(?),?,7,3)", id, id, province, enabled);
        jdbc.update("INSERT INTO identity_user_role (user_id,role_code) VALUES (UUID_TO_BIN(?),?)", id, role);
        return id;
    }
}
