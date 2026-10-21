package com.pluto.ndi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.backup.domain.*;
import com.pluto.ndi.backup.mapper.BackupTaskMapper;
import com.pluto.ndi.backup.service.*;
import com.pluto.ndi.backup.util.BackupChecksum;
import com.pluto.ndi.documents.service.BackupObjectDirectory;
import com.pluto.ndi.shared.web.BusinessException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = NdiApplication.class, properties = {
        "ndi.backup.enabled=true", "ndi.backup.initial-delay-ms=600000", "ndi.backup.max-uncompressed-bytes=1048576",
        "ndi.projects.export.initial-delay-ms=600000", "ndi.approvals.reminders-enabled=false", "ndi.projects.inactivity.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BackupTaskIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @TempDir static Path directory;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ndi.backup.directory", () -> directory.toString());
    }
    @Autowired BackupTaskService tasks;
    @Autowired BackupTaskWorker worker;
    @Autowired BackupTaskMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired DataSource source;
    @Autowired ObjectMapper json;
    @Autowired com.pluto.ndi.identity.service.IdentityDirectory identities;
    @Autowired com.pluto.ndi.shared.audit.AuditWriter audit;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean BackupObjectDirectory objects;
    UUID admin;
    UUID reader;
    @BeforeEach void seed() {
        jdbc.update("DELETE FROM system_backup_task");
        admin = account("SYSTEM_ADMIN"); reader = account("GROUP_REVIEWER");
    }
    private UUID account(String role) {
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,enabled) VALUES (UUID_TO_BIN(?),?,'备份测试',true)", id.toString(),id.toString());
        jdbc.update("INSERT INTO identity_user_role(user_id,role_code) VALUES (UUID_TO_BIN(?),?)",id.toString(),role);
        return id;
    }
    @Test void createsOnceProcessesAndStreamsVerifiedArchiveWithAudit() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/backups").header("X-NDI-Actor-Id",admin.toString()).contentType("application/json").content(json.writeValueAsString(Map.of("requestId",id))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING")).andExpect(jsonPath("$.data.workerToken").doesNotExist());
        assertThat(tasks.create(admin,id).id()).isEqualTo(id);
        assertThatThrownBy(() -> tasks.create(admin,UUID.randomUUID())).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.errorCode()).isEqualTo(BackupErrorCode.BUSY));
        mvc.perform(get("/api/v1/admin/backups/{id}/download",id).header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isConflict());
        worker.runPending();
        var complete = tasks.find(admin,id);
        assertThat(complete.status()).isEqualTo("SUCCEEDED");
        assertThat(complete.sha256()).isEqualTo(BackupChecksum.sha256(directory.resolve(id + ".zip")));
        mvc.perform(post("/api/v1/admin/backups/{id}/download-check",id).header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isOk());
        var response = mvc.perform(get("/api/v1/admin/backups/{id}/download",id).header("X-NDI-Actor-Id",admin.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Checksum-SHA256",complete.sha256())).andReturn().getResponse();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.getContentAsByteArray()))).isEqualTo(complete.sha256());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE resource_id=UUID_TO_BIN(?) AND event_type='BACKUP_REQUESTED'",Long.class,id.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE resource_id=UUID_TO_BIN(?) AND event_type='BACKUP_DOWNLOAD_INTENT'",Long.class,id.toString())).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/backups").header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true)).andExpect(jsonPath("$.data.records.totalElements").value(1));
    }
    @Test void rejectsUnauthorizedRevokedAndMalformedAccess() throws Exception {
        mvc.perform(get("/api/v1/admin/backups")).andExpect(status().isUnauthorized());
        for (String path : List.of("/api/v1/admin/backups", "/api/v1/admin/backups/" + UUID.randomUUID() + "/download")) {
            mvc.perform(get(path).header("X-NDI-Actor-Id",reader.toString())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/admin/backups").header("X-NDI-Actor-Id",reader.toString()).contentType("application/json").content(json.writeValueAsString(Map.of("requestId",UUID.randomUUID())))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/backups").header("X-NDI-Actor-Id",admin.toString()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/backups?size=101").header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isBadRequest());
        var id = tasks.create(admin,UUID.randomUUID()).id();
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE id=UUID_TO_BIN(?)",admin.toString());
        worker.runPending();
        var failed = mapper.find(id).orElseThrow();
        assertThat(failed.errorCode()).isEqualTo("ACTOR_REVOKED");
        assertThat(Files.exists(directory.resolve(id + ".zip"))).isFalse();
        assertThatThrownBy(() -> tasks.find(admin,id)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsChangedMissingAndSymlinkArchives() throws Exception {
        var id = tasks.create(admin,UUID.randomUUID()).id(); worker.runPending();
        var path = directory.resolve(id + ".zip");
        Files.write(path,new byte[]{1},StandardOpenOption.APPEND);
        mvc.perform(get("/api/v1/admin/backups/{id}/download",id).header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isGone());
        Files.delete(path);
        mvc.perform(post("/api/v1/admin/backups/{id}/download-check",id).header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isGone());
        var another = directory.resolve("unrelated.txt"); Files.writeString(another,"不允许读取"); Files.createSymbolicLink(path,another);
        mvc.perform(get("/api/v1/admin/backups/{id}/download",id).header("X-NDI-Actor-Id",admin.toString())).andExpect(status().isGone());
    }
    @Test void recoversInterruptedTasksAndFencesOldWorkerWrites() {
        var id = tasks.create(admin,UUID.randomUUID()).id(); var old = tasks.claim().orElseThrow();
        worker.runPending();
        assertThat(tasks.find(admin,id).errorCode()).isEqualTo("INTERRUPTED");
        var manifest = new BackupManifest(1,id,Instant.now(),"0".repeat(64),List.of(),List.of());
        tasks.complete(old,new BackupArchiveService.Archive(id,directory.resolve(id + ".zip"),1,"0".repeat(64),manifest));
        assertThat(tasks.find(admin,id).status()).isEqualTo("FAILED");
        assertThat(tasks.create(admin,UUID.randomUUID()).status()).isEqualTo("PENDING");
    }
    @Test void serializesWorkerRecoveryAndExecutionWithDatabaseLock() throws Exception {
        var id = tasks.create(admin,UUID.randomUUID()).id();
        String lock = "ndi.backup.worker." + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("ndi".getBytes(StandardCharsets.UTF_8))).substring(0,32);
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
            statement.setString(1,lock); statement.executeQuery().close();
            try { worker.runPending(); assertThat(tasks.find(admin,id).status()).isEqualTo("PENDING"); }
            finally { try(var release=connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {release.setString(1,lock);release.executeQuery().close();} }
        }
        worker.runPending(); assertThat(tasks.find(admin,id).status()).isEqualTo("SUCCEEDED");
    }
    @Test void keepsFailureDurableAndDoesNotPublishPartialArchive() {
        jdbc.execute("CREATE TABLE backup_failure_fixture (payload LONGTEXT) ENGINE=InnoDB");
        jdbc.execute("INSERT INTO backup_failure_fixture VALUES (REPEAT('x',1000000))");
        var id=tasks.create(admin,UUID.randomUUID()).id();
        try {worker.runPending();} finally {jdbc.execute("DROP TABLE backup_failure_fixture");}
        assertThat(tasks.find(admin,id).status()).isEqualTo("FAILED");
        assertThat(tasks.find(admin,id).errorCode()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(Files.exists(directory.resolve(id + ".zip"))).isFalse();
    }
    @Test void concurrentIdempotentRequestsKeepOneTaskAndOneAudit() throws Exception {
        var id=UUID.randomUUID(); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> { start.await(); return tasks.create(admin,id); });
            var second=pool.submit(() -> { start.await(); return tasks.create(admin,id); });
            start.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS).id()).isEqualTo(id);
            assertThat(second.get(15,TimeUnit.SECONDS).id()).isEqualTo(id);
        }
        assertThat(tasks.list(admin,PageRequest.of(0,20)).getTotalElements()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE resource_id=UUID_TO_BIN(?) AND event_type='BACKUP_REQUESTED'",Long.class,id.toString())).isEqualTo(1);
        var other=account("SYSTEM_ADMIN");
        assertThatThrownBy(() -> tasks.create(other,id)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.errorCode()).isEqualTo(BackupErrorCode.REQUEST_CONFLICT));
    }
    @Test void disabledConfigurationRejectsCreationWithoutWritingRecords() {
        var disabled = new BackupTaskService(mapper,identities,
                new com.pluto.ndi.backup.config.BackupProperties(directory,1048576,10000,120,false),audit,java.time.Clock.systemUTC(),manager);
        assertThatThrownBy(() -> disabled.create(admin,UUID.randomUUID())).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo(BackupErrorCode.DISABLED));
        assertThat(tasks.list(admin,PageRequest.of(0,20)).getTotalElements()).isZero();
    }
}
