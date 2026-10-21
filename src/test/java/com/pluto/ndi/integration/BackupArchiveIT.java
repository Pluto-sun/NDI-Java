package com.pluto.ndi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.backup.config.BackupProperties;
import com.pluto.ndi.backup.domain.BackupFailure;
import com.pluto.ndi.backup.service.BackupArchiveService;
import com.pluto.ndi.backup.util.BackupArchiveVerifier;
import com.pluto.ndi.documents.service.BackupObjectDirectory;
import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.projects.service.CreateProjectCommand;
import com.pluto.ndi.projects.service.ProjectService;
import io.minio.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.*;
import java.util.zip.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static com.pluto.ndi.backup.domain.BackupFailure.Reason.*;

@SpringBootTest(classes = NdiApplication.class, properties = {
        "ndi.projects.export.initial-delay-ms=600000", "ndi.approvals.reminders-enabled=false", "ndi.projects.inactivity.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class BackupArchiveIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @Container static final MySQLContainer<?> RESTORED = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi_restored").withUsername("ndi").withPassword("ndi_test_password");
    private static GenericContainer<?> minio() {
        return new GenericContainer<>("netiedge/minio:RELEASE.2025-01-20T14-49-07Z@sha256:e6c282005f6c8496256f8fd07e9a8b82073344c217ea87436d9af888db67eb0e")
                .withExposedPorts(9000).withEnv("MINIO_ROOT_USER", "ndi_backup_test")
                .withEnv("MINIO_ROOT_PASSWORD", "ndi_backup_test_password").withCommand("server", "/data")
                .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
    }
    @Container static final GenericContainer<?> MINIO = minio();
    @Container static final GenericContainer<?> RESTORED_MINIO = minio();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.storage.endpoint", () -> endpoint(MINIO));
        registry.add("ndi.storage.public-endpoint", () -> endpoint(MINIO));
        registry.add("ndi.storage.access-key", () -> "ndi_backup_test");
        registry.add("ndi.storage.secret-key", () -> "ndi_backup_test_password");
        registry.add("ndi.storage.bucket", () -> "ndi-backup-it");
        registry.add("ndi.storage.auto-create-bucket", () -> true);
    }
    @Autowired DataSource source;
    @Autowired BackupObjectDirectory objects;
    @Autowired ExportFileDirectory exports;
    @Autowired ObjectMapper json;
    @Autowired ProjectService projects;
    @Autowired com.pluto.ndi.projects.service.ProjectApprovalService approvals;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;
    private UUID user;
    private UUID project;
    private static final byte[] PDF = "%PDF-1.7\n备份资料 中文🙂\0\\'".getBytes(StandardCharsets.UTF_8);
    private static final String TEXT = "中文🙂 引号' 反斜杠\\ 换行\n空字节\0与SQL; --";

    @BeforeEach void seed() throws Exception {
        jdbc.update("DELETE FROM document_object");
        jdbc.update("DELETE FROM project_export_download");
        jdbc.update("DELETE FROM project_export_task");
        var province = UUID.randomUUID(); var city = UUID.randomUUID(); user = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province (id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,'备份省',true)", province.toString(), province.toString().replace("-", ""));
        jdbc.update("INSERT INTO organization_city (id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,'备份市',true)", city.toString(), province.toString(), city.toString().replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,province_id,enabled) VALUES (UUID_TO_BIN(?),?,'备份负责人',UUID_TO_BIN(?),true)", user.toString(), user.toString(), province.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id,role_code) VALUES (UUID_TO_BIN(?),'PROVINCE_REPORTER')", user.toString());
        project = projects.createProject(user, new CreateProjectCommand(province, city, "备份测试-" + UUID.randomUUID(),
                TEXT, "C25", TEXT, new BigDecimal("12345.67"), user, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("120.34"))), TEXT, "后续动作")).id();
        var reviewer = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,enabled) VALUES (UUID_TO_BIN(?),?,'备份接口人',true)", reviewer.toString(), reviewer.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id,role_code) VALUES (UUID_TO_BIN(?),'INTERFACE_REVIEWER')", reviewer.toString());
        jdbc.update("INSERT INTO identity_reviewer_province (reviewer_id,province_id) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?))", reviewer.toString(), province.toString());
        approvals.submitMajorChange(user, project, new com.pluto.ndi.projects.service.MajorChangeCommand(
                com.pluto.ndi.projects.domain.MajorChangeType.TOTAL_AMOUNT, com.pluto.ndi.identity.service.ProjectInitiatorLevel.PROVINCE,
                reviewer, "验证生成列恢复", null, new BigDecimal("13000.00"), null));
        var client = client(MINIO);
        if (!client.bucketExists(BucketExistsArgs.builder().bucket("ndi-backup-it").build())) client.makeBucket(MakeBucketArgs.builder().bucket("ndi-backup-it").build());
        jdbc.execute("CREATE TABLE IF NOT EXISTS backup_value_fixture (id INT PRIMARY KEY, text_value LONGTEXT, raw_value LONGBLOB, amount DECIMAL(18,6), flag BOOLEAN, document JSON, happened DATETIME(6), nullable_value VARCHAR(20)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.update("DELETE FROM backup_value_fixture");
        jdbc.update("INSERT INTO backup_value_fixture VALUES (1,?,?,123.456789,true,JSON_OBJECT('内容',?),'2026-10-21 12:34:56.123456',NULL)", TEXT, new byte[]{0, 39, -1, 92}, TEXT);
        jdbc.update("INSERT INTO backup_value_fixture VALUES (2,'负值',NULL,-12.000001,false,NULL,NULL,'非空')");
    }

    @Test void restoresAllTablesAndFormalObjectsIntoIndependentMysqlAndMinio() throws Exception {
        document("AVAILABLE", PDF, true);
        document("DELETED", PDF, true);
        document("PENDING", PDF, false);
        var exportId = UUID.randomUUID();
        jdbc.update("INSERT INTO project_export_task (id,requested_by,scope_level,filter_json,status,requested_at) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),'NATIONAL',JSON_OBJECT(),'COMPLETED',UTC_TIMESTAMP(6))", exportId.toString(), user.toString());
        exports.storeCsv(exportId, "编号,名称\r\n1,备份导出\r\n".getBytes(StandardCharsets.UTF_8));
        var properties = new BackupProperties(directory, 20_000_000, 10000, 120);
        var mapper = new com.pluto.ndi.backup.mapper.MysqlBackupMapper(properties, objects) {
            @Override public long dump(java.sql.Connection connection, String table, Writer sql, Runnable checkBudget) throws java.sql.SQLException, IOException {
                long count = super.dump(connection, table, sql, checkBudget);
                if (table.equals("identity_user_account")) {
                    // 在快照连接之外提交业务写入，后续项目查询和对象清单仍必须保持旧版本。
                    jdbc.update("UPDATE project_project SET customer_name='快照后的新客户' WHERE id=UUID_TO_BIN(?)", project.toString());
                    try { document("AVAILABLE", PDF, false); }
                    catch (Exception failure) { throw new IOException(failure); }
                }
                return count;
            }
        };
        var archive = new BackupArchiveService(source, objects, properties, json, Clock.systemUTC(), mapper).create(UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT customer_name FROM project_project WHERE id=UUID_TO_BIN(?)", String.class, project.toString())).isEqualTo("快照后的新客户");
        var manifest = BackupArchiveVerifier.verify(archive.path(), archive.sha256(), 20_000_000, json);
        assertThat(manifest.objects()).hasSize(3);
        assertThat(Files.getPosixFilePermissions(archive.path())).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        assertThatThrownBy(() -> engine(20_000_000).create(archive.id())).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(BUSY));
        try (var zip = new ZipFile(archive.path().toFile()); var connection = DriverManager.getConnection(RESTORED.getJdbcUrl(), RESTORED.getUsername(), RESTORED.getPassword())) {
            byte[] sql;
            try (var input = zip.getInputStream(zip.getEntry("database.sql"))) { sql = input.readAllBytes(); }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(sql), StandardCharsets.UTF_8));
            for (var table : manifest.tables()) {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM `" + table.name().replace("`", "``") + "`")) {
                    rows.next(); assertThat(rows.getLong(1)).as(table.name()).isEqualTo(table.rows());
                }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM backup_value_fixture WHERE id=1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("text_value")).isEqualTo(TEXT);
                assertThat(rows.getBytes("raw_value")).containsExactly(0, 39, -1, 92);
                assertThat(rows.getBigDecimal("amount")).isEqualByComparingTo("123.456789");
                assertThat(rows.getBoolean("flag")).isTrue();
                assertThat(json.readTree(rows.getString("document")).get("内容").asText()).isEqualTo(TEXT);
                assertThat(rows.getString("happened")).isEqualTo("2026-10-21 12:34:56.123456");
                assertThat(rows.getString("nullable_value")).isNull();
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT flag,amount FROM backup_value_fixture WHERE id=2")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isFalse();
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("-12.000001");
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM project_approval_request WHERE status='PENDING' AND pending_project_id=project_id")) {
                rows.next(); assertThat(rows.getLong(1)).isGreaterThan(0);
            }
            try (var statement = connection.prepareStatement("SELECT customer_name,total_amount,city_id FROM project_project WHERE id=UUID_TO_BIN(?)")) {
                statement.setString(1, project.toString());
                try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(TEXT); assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("12345.67"); assertThat(rows.getBytes(3)).hasSize(16); }
            }
            var restored = client(RESTORED_MINIO);
            for (var object : manifest.objects()) {
                if (!restored.bucketExists(BucketExistsArgs.builder().bucket(object.bucket()).build())) restored.makeBucket(MakeBucketArgs.builder().bucket(object.bucket()).build());
                try (var input = zip.getInputStream(zip.getEntry(object.entry()))) {
                    restored.putObject(PutObjectArgs.builder().bucket(object.bucket()).object(object.objectKey()).stream(input, object.size(), -1L).contentType(object.mediaType()).build());
                }
                try (var input = restored.getObject(GetObjectArgs.builder().bucket(object.bucket()).object(object.objectKey()).build())) {
                    assertThat(hex(input.readAllBytes())).isEqualTo(object.sha256());
                }
            }
        }
        // 归档编号、清单和源数据库中均不保存连接口令。
        assertThat(json.writeValueAsString(manifest)).doesNotContain("ndi_test_password", "ndi_backup_test_password");
    }

    @Test void rejectsMissingAndCorruptedObjectsAndRemovesPartialArchives() throws Exception {
        var missing = document("AVAILABLE", PDF, false);
        assertThatThrownBy(() -> engine(20_000_000).create(UUID.randomUUID())).isInstanceOf(BackupFailure.class);
        jdbc.update("DELETE FROM document_object WHERE id=UUID_TO_BIN(?)", missing.toString());
        // 长度相同仍须检查摘要，不能只按文件大小判断完整性。
        document("AVAILABLE", new byte[PDF.length], true);
        assertThatThrownBy(() -> engine(20_000_000).create(UUID.randomUUID())).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(INTEGRITY_FAILED));
        try (var paths = Files.list(directory)) { assertThat(paths.toList()).isEmpty(); }
    }

    @Test void rejectsLimitsAndUnsupportedStructuresWithoutPublishing() throws Exception {
        assertThatThrownBy(() -> engine(32).create(UUID.randomUUID())).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(LIMIT_EXCEEDED));
        jdbc.execute("CREATE TABLE backup_nontransactional_test (id INT) ENGINE=MyISAM");
        try {
            assertThatThrownBy(() -> engine(20_000_000).create(UUID.randomUUID())).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(UNSUPPORTED_SCHEMA));
        } finally { jdbc.execute("DROP TABLE backup_nontransactional_test"); }
        try (var paths = Files.list(directory)) { assertThat(paths.toList()).isEmpty(); }
    }

    @Test void verifierRejectsTamperingAndExtraArchivePaths() throws Exception {
        var archive = engine(20_000_000).create(UUID.randomUUID());
        assertThatThrownBy(() -> BackupArchiveVerifier.verify(archive.path(), "0".repeat(64), 20_000_000, json)).isInstanceOf(BackupFailure.class);
        var altered = directory.resolve("altered.zip");
        try (var sourceZip = new ZipFile(archive.path().toFile()); var target = new ZipOutputStream(Files.newOutputStream(altered))) {
            var entries = sourceZip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); target.putNextEntry(new ZipEntry(entry.getName()));
                try (var input = sourceZip.getInputStream(entry)) { input.transferTo(target); }
                target.closeEntry();
            }
            target.putNextEntry(new ZipEntry("../unexpected")); target.write(1); target.closeEntry();
        }
        assertThatThrownBy(() -> BackupArchiveVerifier.verify(altered, BackupArchiveService.sha256(altered), 20_000_000, json)).isInstanceOf(BackupFailure.class);
        assertThatThrownBy(() -> BackupArchiveVerifier.verify(archive.path(), archive.sha256(), 32, json)).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(LIMIT_EXCEEDED));
        var sqlChanged = directory.resolve("sql-changed.zip");
        try (var sourceZip = new ZipFile(archive.path().toFile()); var target = new ZipOutputStream(Files.newOutputStream(sqlChanged))) {
            var entries = sourceZip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); target.putNextEntry(new ZipEntry(entry.getName()));
                try (var input = sourceZip.getInputStream(entry)) { input.transferTo(target); }
                if (entry.getName().equals("database.sql")) target.write("-- altered\n".getBytes(StandardCharsets.UTF_8));
                target.closeEntry();
            }
        }
        assertThatThrownBy(() -> BackupArchiveVerifier.verify(sqlChanged, BackupArchiveService.sha256(sqlChanged), 20_000_000, json)).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(INTEGRITY_FAILED));
    }

    @Test void serializesBackupWorkersUsingDatabaseSessionLock() throws Exception {
        String lock = "ndi.backup." + hex("ndi".getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        try (var connection = source.getConnection(); var query = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
            query.setString(1, lock);
            try (var result = query.executeQuery()) { result.next(); assertThat(result.getInt(1)).isEqualTo(1); }
            try {
                assertThatThrownBy(() -> engine(20_000_000).create(UUID.randomUUID())).isInstanceOfSatisfying(BackupFailure.class, failure -> assertThat(failure.reason()).isEqualTo(BUSY));
            } finally {
                try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) { release.setString(1, lock); release.executeQuery().close(); }
            }
        }
        assertThat(engine(20_000_000).create(UUID.randomUUID()).manifest().tables()).isNotEmpty();
    }

    private UUID document(String status, byte[] actual, boolean present) throws Exception {
        var id = UUID.randomUUID(); String key = "objects/" + id;
        jdbc.update("""
                INSERT INTO document_object (id,scope_type,scope_id,purpose_code,bucket_name,staging_object_key,object_key,original_name,media_type,byte_size,sha256_hex,status,uploaded_by,created_at)
                VALUES (UUID_TO_BIN(?),'PROJECT',UUID_TO_BIN(?),'GENERAL','ndi-backup-it',?,?,'资料.pdf','application/pdf',?,?,?,UUID_TO_BIN(?),UTC_TIMESTAMP(6))
                """, id.toString(), project.toString(), "staging/" + id, key, PDF.length, hex(PDF), status, user.toString());
        if (present) client(MINIO).putObject(PutObjectArgs.builder().bucket("ndi-backup-it").object(key).stream(new ByteArrayInputStream(actual), (long) actual.length, -1L).contentType("application/pdf").build());
        return id;
    }
    private BackupArchiveService engine(long limit) {
        var properties = new BackupProperties(directory, limit, 10000, 120);
        return new BackupArchiveService(source, objects, properties, json, Clock.systemUTC(), new com.pluto.ndi.backup.mapper.MysqlBackupMapper(properties, objects));
    }
    private static String endpoint(GenericContainer<?> container) { return "http://" + container.getHost() + ":" + container.getMappedPort(9000); }
    private static MinioClient client(GenericContainer<?> container) { return MinioClient.builder().endpoint(endpoint(container)).credentials("ndi_backup_test", "ndi_backup_test_password").build(); }
    private static String hex(byte[] content) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
}
