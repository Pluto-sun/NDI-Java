package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.projects.service.ProjectImportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = NdiApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class ProjectImportIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }

    @Autowired private ProjectImportService imports;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private DocumentDirectory documentDirectory;
    @MockitoBean private ExportFileDirectory exportFileDirectory;

    @Test
    void keepsFirstDuplicateAndRejectsMissingAmountThenConfirmsOnce() throws Exception {
        UUID province = UUID.randomUUID();
        UUID city = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province(id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,?,true)",
                province.toString(), province.toString().replace("-", ""), "广东省");
        jdbc.update("INSERT INTO organization_city(id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,true)",
                city.toString(), province.toString(), city.toString().replace("-", ""), "广州市");
        addUser(actor, province, city, "city-test", "地市导入人", "CITY_REPORTER");
        addUser(leader, province, null, "leader-test", "省分负责人", "PROVINCE_REPORTER");

        var preview = imports.preview(actor, "广东项目.xlsx", workbook());
        assertThat(preview.totalRows()).isEqualTo(3);
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.failedRows()).isEqualTo(2);
        assertThat(preview.rows().get(1).errorsJson()).contains("重复项目");
        assertThat(preview.rows().get(2).errorsJson()).contains("项目总金额");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class)).isZero();

        var done = imports.confirm(actor, preview.batchId());
        assertThat(done.importedRows()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_progress", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_source_record", Integer.class)).isEqualTo(1);
        assertThat(done.rows().getFirst().status()).isEqualTo("IMPORTED");
    }

    private void addUser(UUID id, UUID province, UUID city, String login, String name, String role) {
        jdbc.update("""
                INSERT INTO identity_user_account(id,login_name,display_name,province_id,city_id,enabled)
                VALUES (UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),?,true)
                """, id.toString(), login, name, province.toString(), city == null ? null : uuidBytes(city));
        jdbc.update("INSERT INTO identity_user_role(user_id,role_code) VALUES (UUID_TO_BIN(?),?)", id.toString(), role);
    }

    private byte[] uuidBytes(UUID value) {
        var buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private byte[] workbook() throws Exception {
        var headers = List.of("省分", "地市", "客户名称", "客户所属行业", "项目名称",
                "省分负责人", "地市客户经理", "项目建设内容", "三化类别", "项目阶段",
                "项目总金额（万）", "网络化预计金额（万）", "格物预计金额（万）",
                "大数据预计金额（万）", "万悟预计金额（万）", "项目进展（2026年10月10日）");
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("导入数据");
            for (int rowIndex = 0; rowIndex <= 3; rowIndex++) {
                var row = sheet.createRow(rowIndex);
                var values = rowIndex == 0 ? headers : List.of("广东省", "广州市", "某客户",
                        "计算机、通信和其他电子设备制造业", rowIndex == 3 ? "缺金额项目" : "首条项目",
                        "省分负责人", "张经理", "客户需要建设平台", "数字化（格物）", "方案交流",
                        rowIndex == 3 ? "" : "100", "", "50", "", "", "完成交流");
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
