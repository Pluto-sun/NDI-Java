package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluto.ndi.capabilities.DevelopmentAgentController;
import com.pluto.ndi.capabilities.IntelligenceKind;
import com.pluto.ndi.capabilities.IntelligenceService;
import com.pluto.ndi.capabilities.GewuDeploymentController;
import com.pluto.ndi.capabilities.GewuImportService;
import com.pluto.ndi.capabilities.LiveAgentController;
import com.pluto.ndi.capabilities.WangwuController;
import com.pluto.ndi.shared.imports.ImportControllerBase;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.imports.ImportModels.Configuration;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GewuImportIT {
    private static final List<String> HEADERS =
            List.of("接口人", "省份", "地市", "客户名称", "项目名称", "制造类型",
                    "国民经济行业分类", "所属行业", "部署版本", "上一周期是否已接设备");

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>("netiedge/minio:RELEASE.2025-01-20T14-49-07Z")
                    .withEnv("MINIO_ROOT_USER", "ndi_test_admin")
                    .withEnv("MINIO_ROOT_PASSWORD", "ndi_test_password")
                    .withCommand("server /data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add(
                "ndi.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add(
                "ndi.storage.public-endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        r.add("ndi.storage.access-key", () -> "ndi_test_admin");
        r.add("ndi.storage.secret-key", () -> "ndi_test_password");
        r.add("ndi.storage.auto-create-bucket", () -> "true");
    }

    @Autowired IntelligenceService intelligence;
    @Autowired GewuImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired GewuDeploymentController gewu;
    @Autowired WangwuController wangwu;
    @Autowired LiveAgentController liveAgents;
    @Autowired DevelopmentAgentController developmentAgents;

    private UUID admin() {
        var actor = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO identity_user_account(id, login_name, display_name, enabled)
                VALUES(UUID_TO_BIN(?), ?, '导入测试', TRUE)
                """,
                actor.toString(), "gewu-" + actor);
        jdbc.update(
                "INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?), 'SYSTEM_ADMIN')",
                actor.toString());
        return actor;
    }

    @Test
    void unmatchedLocationRemainsAnEditableRowError() throws Exception {
        var actor = admin();
        var uploaded = imports.upload(actor, "格物部署.xlsx", workbook(), null, null);
        var configuration = new Configuration(
                "格物部署", 1, HEADERS.stream().collect(Collectors.toMap(Function.identity(), Function.identity())));
        var mapped = imports.save(actor, uploaded.id(), uploaded.version(), 2, configuration, null);
        var preview = imports.save(actor, mapped.id(), mapped.version(), 3, configuration, null);

        assertThat(preview.batch().validRows()).isEqualTo(1);
        assertThat(preview.batch().failedRows()).isEqualTo(1);
        assertThat(preview.batch().rows().get(1).errorsJson()).contains("省份和地市须匹配启用的组织");
        assertThat(imports.current(actor).step()).isEqualTo(3);

        var batch = preview.batch().batchId();
        try (var exported = new XSSFWorkbook(new ByteArrayInputStream(imports.originalFailedRows(actor, batch)))) {
            var sheet = exported.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("地市");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("无效地市");
            assertThat(sheet.getRow(1).getCell(10).getStringCellValue()).isEqualTo("来源行2");
            int reasonColumn = sheet.getRow(0).getLastCellNum() - 1;
            assertThat(sheet.getRow(0).getCell(reasonColumn).getStringCellValue()).isEqualTo("失败原因");
            assertThat(sheet.getRow(1).getCell(reasonColumn).getStringCellValue())
                    .isEqualTo("省份和地市须匹配启用的组织");
        }
        assertThatThrownBy(() -> imports.originalFailedRows(admin(), batch))
                .isInstanceOf(BusinessException.class);
        var invalid = imports.revise(actor, batch, 3, 0, Map.of("地市", "深圳市", "接口人", ""));
        assertThat(invalid.failedRows()).isEqualTo(1);
        var taskBeforeExport = imports.current(actor);
        // 模拟旧任务仅保存失败状态、缺少原因明细。
        jdbc.update("""
                UPDATE capability_import_task SET task_json = JSON_SET(task_json, '$.batch.rows[1].errorsJson', '[]')
                WHERE actor_id=UUID_TO_BIN(?)
                """, actor.toString());
        try (var exported = new XSSFWorkbook(new ByteArrayInputStream(imports.originalFailedRows(actor, batch)))) {
            var sheet = exported.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("无效地市");
            assertThat(sheet.getRow(1).getCell(sheet.getRow(0).getLastCellNum() - 1).getStringCellValue())
                    .contains("接口人").doesNotContain("省份和地市");
        }
        var afterExport = imports.current(actor);
        assertThat(afterExport.version()).isEqualTo(taskBeforeExport.version());
        assertThat(afterExport.step()).isEqualTo(3);
        assertThat(afterExport.batch().rows().get(1).errorsJson()).isEqualTo("[]");
        var corrected = imports.revise(actor, batch, 3, 1, Map.of("接口人", "导入验收接口人"));
        assertThat(corrected.validRows()).isEqualTo(2);
        assertThatThrownBy(() -> imports.originalFailedRows(actor, batch))
                .hasMessageContaining("没有预检失败");
        var competing = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO capability_gewu_deployment(id,province_id,city_id,details,updated_at)
                SELECT UUID_TO_BIN(?),province_id,id,JSON_OBJECT('项目名称','测试部署2'),NOW(6)
                FROM organization_city WHERE city_code='440300'
                """, competing);
        assertThatThrownBy(() -> imports.confirm(actor, batch, List.of(2, 3)))
                .hasMessageContaining("已不满足导入条件");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM capability_gewu_deployment", Integer.class))
                .isEqualTo(1);
        jdbc.update("UPDATE capability_gewu_deployment SET deleted_at=NOW(6) WHERE id=UUID_TO_BIN(?)",
                competing);
        assertThat(imports.confirm(actor, batch, List.of(2, 3)).importedRows()).isEqualTo(2);
        assertThat(imports.current(actor)).isNull();
    }

    @Test
    void allCapabilityTemplatesAndIntelligenceFailureExportsUseTheSharedWorkflow() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new ActorPrincipal(admin()), null, List.of());
        var expected = Map.of(
                gewu, List.of("省份", "地市", "上一周期是否已接设备", "是否最新工具包", "是否可升级工具包"),
                wangwu, List.of("省分", "部署进度"),
                liveAgents, List.of("省分", "环节", "是否为三方生态迁移", "是否计收", "是否在计收表中出现"),
                developmentAgents, List.of("省分", "环节", "状态", "是否为三方生态迁移", "是否计收"));
        for (var item : expected.entrySet()) {
            var controller = item.getKey();
            var template = controller.template(auth).getBody();
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(template));
                    var output = new ByteArrayOutputStream()) {
                var sheet = book.getSheetAt(0);
                var header = sheet.getRow(0);
                assertThat(sheet.getDataValidations()).extracting(rule ->
                        header.getCell(rule.getRegions().getCellRangeAddresses()[0].getFirstColumn())
                                .getStringCellValue()).containsExactlyInAnyOrderElementsOf(item.getValue());
                assertThat(book.isSheetHidden(1)).isTrue();
                if (controller == gewu) continue;
                var row = sheet.createRow(1);
                row.createCell(1).setCellValue("无效省分");
                book.write(output);
                var upload = controller.upload(auth,
                        new MockMultipartFile("file", "测试.xlsx", null, output.toByteArray()),
                        null, null).data();
                var mapping = new java.util.LinkedHashMap<String, String>();
                for (var cell : header) mapping.put(cell.getStringCellValue(), cell.getStringCellValue());
                var config = new Configuration(sheet.getSheetName(), 1, mapping);
                var mapped = controller.save(auth,
                        new ImportControllerBase.Save(upload.id(), upload.version(), 2, config, null)).data();
                var preview = controller.save(auth,
                        new ImportControllerBase.Save(mapped.id(), mapped.version(), 3, config, null)).data();
                assertThat(preview.batch().failedRows()).isEqualTo(1);
                var bytes = controller.originalFailedRows(auth, preview.batch().batchId()).getBody();
                try (var exported = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    assertThat(exported.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                            .isEqualTo("无效省分");
                    var sheetOut = exported.getSheetAt(0);
                    var reason = sheetOut.getRow(1).getCell(sheetOut.getRow(0).getLastCellNum() - 1);
                    assertThat(preview.batch().rows().getFirst().errorsJson()).contains(reason.getStringCellValue());
                }
            }
        }
    }

    @Test
    void intelligenceKeysRespectScopeAndCompletenessAndAreEnforcedInMysql() {
        var actor = admin();
        UUID guangdong = province("440000"), jiangxi = province("360000");
        var live = IntelligenceKind.AGENTS_LIVE;
        var development = IntelligenceKind.AGENTS_DEVELOPMENT;
        var liveFields = Map.of("接口人", "测试接口人", "省分智能化接口人", "测试接口人",
                "智能体功能简述", "测试功能", "是否为三方生态迁移", "否",
                "是否计收", "未计收", "是否同步到计收表", "否", "智能体名称", "重复检查智能体",
                "环节", "研发设计", "应用项目名称", "测试应用项目", "最终客户名称", "客户");
        var liveInput = new IntelligenceService.Input(guangdong, liveFields);
        var saved = intelligence.save(actor, live, null, null, liveInput);
        assertThatThrownBy(() -> intelligence.save(actor, live, null, null, liveInput))
                .hasMessageContaining("相同业务唯一键");
        intelligence.save(actor, live, saved.id(), saved.version(), liveInput);
        intelligence.save(actor, live, null, null, new IntelligenceService.Input(jiangxi, liveFields));
        var otherLiveFields = new java.util.HashMap<>(liveFields);
        otherLiveFields.put("应用项目名称", "另一个应用项目");
        var otherLive = intelligence.save(actor, live, null, null,
                new IntelligenceService.Input(guangdong, otherLiveFields));
        assertThatThrownBy(() -> intelligence.save(actor, live, otherLive.id(), otherLive.version(), liveInput))
                .hasMessageContaining("相同业务唯一键");
        assertThat(intelligence.matchTokenAgents(actor, "广东省", "重复检查智能体", "", "")).hasSize(2);
        assertThat(intelligence.matchTokenAgents(actor, "广东省", "重复检查智能体", "另一个应用项目", ""))
                .extracting(IntelligenceService.Entry::id).containsExactly(otherLive.id());
        otherLiveFields.remove("应用项目名称");
        assertThatThrownBy(() -> intelligence.save(actor, live, null, null,
                new IntelligenceService.Input(guangdong, otherLiveFields)))
                .hasMessageContaining("应用项目名称不能为空");
        for (int i = 0; i < 2; i++) {
            jdbc.update("""
                    INSERT INTO intelligence_capability(id,kind,province_id,details,updated_at)
                    VALUES(UUID_TO_BIN(?),'AGENTS_LIVE',UUID_TO_BIN(?),
                           JSON_OBJECT('智能体名称','旧同名记录'),NOW(6))
                    """, UUID.randomUUID().toString(), guangdong.toString());
        }

        var devFields = Map.of("智能体名称", "智能体", "应用项目名称", "关联项目");
        var devInput = new IntelligenceService.Input(guangdong, devFields);
        var dev = intelligence.save(actor, development, null, null, devInput);
        var other = intelligence.save(actor, development, null, null,
                new IntelligenceService.Input(guangdong, Map.of("智能体名称", "智能体", "应用项目名称", "其他项目")));
        assertThatThrownBy(() -> intelligence.save(actor, development, other.id(), other.version(), devInput))
                .hasMessageContaining("相同业务唯一键");
        assertThatThrownBy(() -> intelligence.save(actor, development, null, null,
                new IntelligenceService.Input(jiangxi, devFields))).hasMessageContaining("相同业务唯一键");
        for (int i = 0; i < 2; i++)
            intelligence.save(actor, development, null, null,
                    new IntelligenceService.Input(guangdong, Map.of("智能体名称", "智能体")));

        for (var record : List.of(saved, dev)) {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO intelligence_capability(id,kind,province_id,city_id,details,updated_at)
                    SELECT UUID_TO_BIN(?),kind,province_id,city_id,details,updated_at
                    FROM intelligence_capability WHERE id=UUID_TO_BIN(?)
                    """, UUID.randomUUID().toString(), record.id().toString()))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }
    }

    private UUID province(String code) {
        return UUID.fromString(jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM organization_province WHERE province_code=?",
                String.class, code));
    }

    private byte[] workbook() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("格物部署");
            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                header.createCell(column).setCellValue(HEADERS.get(column));
            }
            header.createCell(10).setCellValue("来源标记");
            for (int index = 1; index <= 2; index++) {
                var row = sheet.createRow(index);
                var values = List.of("测试接口人", "广东省", index == 1 ? "广州市" : "无效地市",
                        "测试客户", "测试部署" + index, "离散制造", "制造业", "制造业", "1.0", "是");
                row.createCell(10).setCellValue("来源行" + index);
                for (int column = 0; column < values.size(); column++) {
                    row.createCell(column).setCellValue(values.get(column));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
