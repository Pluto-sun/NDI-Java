package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.production.mapper.ProductionPlanMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class ProductionAmountMigrationIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi")
            .withUsername("ndi")
            .withPassword("ndi_test_password");

    @Test
    void migratesEveryOriginalAmountToOrderPreservingNullAndZero() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).target("56").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var id = UUID.randomUUID();
        assertThat(jdbc.update("""
                INSERT INTO production_plan(id,reviewer_name,plan_month,province_id,city_id,project_name,
                    customer_name,normalized_project,normalized_customer,categories,network_amount,
                    gewu_amount,big_data_amount,wangwu_amount,created_by,created_at,updated_at)
                SELECT UUID_TO_BIN(?),'接口人','2026-10-01',province_id,id,'历史项目','客户',
                    '历史项目','客户',JSON_ARRAY('NETWORK','GEWU','BIG_DATA','WANGWU'),
                    0,20,NULL,9.25,UUID_TO_BIN(UUID()),NOW(6),NOW(6)
                FROM organization_city WHERE city_code='440100'
                """, id.toString())).isEqualTo(1);
        Flyway.configure().dataSource(source).load().migrate();

        var mapper = new ProductionPlanMapper(new NamedParameterJdbcTemplate(source), new ObjectMapper());
        var plan = mapper.find(id, false).orElseThrow();
        assertThat(plan.networkOrderAmount()).isEqualByComparingTo("0");
        assertThat(plan.networkAmount()).isEqualByComparingTo("0");
        assertThat(plan.gewuOrderAmount()).isEqualByComparingTo("20");
        assertThat(plan.gewuRevenueAmount()).isNull();
        assertThat(plan.gewuAmount()).isEqualByComparingTo("20");
        assertThat(plan.remark()).isNull();
        assertThat(plan.categories()).containsExactly("GEWU", "WANGWU");
        assertThat(plan.bigDataOrderAmount()).isNull();
        assertThat(plan.bigDataRevenueAmount()).isNull();
        assertThat(plan.bigDataAmount()).isNull();
        assertThat(plan.wangwuOrderAmount()).isEqualByComparingTo("9.25");
        assertThat(plan.wangwuRevenueAmount()).isNull();
        assertThat(plan.wangwuAmount()).isEqualByComparingTo("9.25");
    }
}
