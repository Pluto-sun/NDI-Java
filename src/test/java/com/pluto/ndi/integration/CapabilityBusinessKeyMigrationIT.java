package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class CapabilityBusinessKeyMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @Test
    void duplicateHistoryStopsMigrationBeforeEitherBusinessTableChanges() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target("54").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var insert = """
                INSERT INTO intelligence_capability(id,kind,province_id,details,updated_at)
                SELECT UUID_TO_BIN(UUID()), 'AGENTS_LIVE', id, JSON_OBJECT('智能体名称','重复智能体','应用项目名称','相同应用'), NOW(6)
                FROM organization_province WHERE province_code='440000'
                """;
        jdbc.update(insert);
        jdbc.update(insert);
        var flyway = Flyway.configure().dataSource(source).load();
        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                .hasMessageContaining("Duplicate entry");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM intelligence_capability", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND column_name='business_key'
                  AND table_name IN ('capability_gewu_deployment','intelligence_capability')
                """, Integer.class)).isZero();
    }
}
