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

/** 从四字段约束升级，空值历史兼容且数据库独立阻断六字段重复。 */
@Testcontainers(disabledWithoutDocker = true)
class RevenueSixFieldMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @Test
    void preservesHistoricalDataAndEnforcesEachNewDimension() {
        var datasource =
                new DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(datasource).target("45").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        var id = UUID.randomUUID().toString();
        jdbc.update(
                """
INSERT INTO revenue_entry(id,version_no,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,recognition,recognized_amount,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(?),7,'2026-11-01',TRUE,'SIX','2',100,10,JSON_OBJECT('业务号码/项目编号','0001'),JSON_OBJECT('amount',5),5,UUID_TO_BIN(?),'2026-07-20','2026-07-21')
""",
                id,
                UUID.randomUUID().toString());
        Flyway.configure().dataSource(datasource).load().migrate();
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
                                "SELECT natural_customer_name_hash=UNHEX(SHA2('',256)) AND"
                                    + " product_code_hash=UNHEX(SHA2('',256)) FROM revenue_entry",
                                Boolean.class))
                .isTrue();
        String insert =
                """
INSERT INTO revenue_entry(id,account_month,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
SELECT UUID_TO_BIN(?),account_month,specific_organization,contract_no,'0002',clause_amount,source_amount,JSON_SET(source_fields,'$."自然客户名称"',?,'$."产品编码"',?),created_by,created_at,updated_at FROM revenue_entry WHERE id=UUID_TO_BIN(?)
""";
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID().toString(), "", "", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, UUID.randomUUID().toString(), "客户甲", "", id);
        jdbc.update(insert, UUID.randomUUID().toString(), "", "0002", id);
        jdbc.update(insert, UUID.randomUUID().toString(), "", "2", id);
        jdbc.update(insert, UUID.randomUUID().toString(), "客户甲", "0002", id);
        assertThatThrownBy(
                        () -> jdbc.update(insert, UUID.randomUUID().toString(), "客户甲", "0002", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revenue_entry", Integer.class))
                .isEqualTo(5);
    }
}
