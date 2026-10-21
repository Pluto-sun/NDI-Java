package com.pluto.ndi.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.backup.config.BackupProperties;
import com.pluto.ndi.backup.domain.BackupFailure;
import com.pluto.ndi.backup.domain.BackupManifest;
import com.pluto.ndi.documents.service.BackupObjectDirectory;
import com.pluto.ndi.backup.mapper.MysqlBackupMapper;
import com.pluto.ndi.backup.mapper.MysqlBackupMapper.ObjectRef;
import com.pluto.ndi.backup.util.BackupChecksum;
import com.pluto.ndi.backup.util.BackupArchiveVerifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.pluto.ndi.backup.domain.BackupFailure.Reason.*;

/** 同一数据库快照确定 SQL 与正式对象清单；任何缺失对象或摘要不符均不得发布归档。 */
@Service
public class BackupArchiveService {
    private final DataSource source;
    private final MysqlBackupMapper mapper;
    private final BackupObjectDirectory objects;
    private final BackupProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public BackupArchiveService(DataSource source, BackupObjectDirectory objects, BackupProperties properties,
            ObjectMapper json, Clock clock, MysqlBackupMapper mapper) {
        this.mapper = mapper;
        this.source = source; this.objects = objects; this.properties = properties; this.json = json; this.clock = clock;
    }

    public record Archive(UUID id, Path path, long bytes, String sha256, BackupManifest manifest) { }

    // 此引擎不得加入业务写事务，避免长时间归档占用业务事务并改变调用方隔离级别。
    @Transactional(propagation = Propagation.NEVER)
    public Archive create(UUID id) {
        Objects.requireNonNull(id);
        Path temporary = null;
        try (var connection = source.getConnection()) {
            String timezone;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT @@session.time_zone")) {
                rows.next(); timezone = rows.getString(1);
            }
            String lock = "ndi.backup." + hash(connection.getCatalog().getBytes(StandardCharsets.UTF_8)).substring(0, 32);
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, lock);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new BackupFailure(BUSY);
                }
            }
            try {
                var directory = properties.directory().toAbsolutePath().normalize();
                if (!Files.exists(directory)) Files.createDirectories(directory,
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw new BackupFailure(STORAGE_FAILED);
                }
                var target = directory.resolve(id + ".zip");
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new BackupFailure(BUSY);
                temporary = Files.createTempFile(directory, "backup-" + id + "-", ".partial",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                var budget = new Budget(properties.maxUncompressedBytes(), properties.timeoutSeconds());
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.execute("SET time_zone = '+00:00'");
                    statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
                }
                var snapshotAt = Instant.now(clock);
                var tables = mapper.schema(connection);
                var references = mapper.references(connection);
                BackupManifest manifest;
                try (var zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                    zip.putNextEntry(new ZipEntry("database.sql"));
                    var digest = digest();
                    var sql = new OutputStreamWriter(new DigestOutputStream(new BudgetOutput(zip, budget), digest), StandardCharsets.UTF_8);
                    sql.write("-- NDI 完整逻辑备份；仅向空库恢复，备份期间禁止执行 DDL。\nSET NAMES utf8mb4;\nSET time_zone='+00:00';\nSET FOREIGN_KEY_CHECKS=0;\n");
                    var counts = new ArrayList<BackupManifest.TableCount>();
                    for (var table : tables.entrySet()) {
                        budget.check();
                        sql.write(table.getValue() + ";\n");
                        counts.add(new BackupManifest.TableCount(table.getKey(), mapper.dump(connection, table.getKey(), sql, budget::check)));
                    }
                    sql.write("SET FOREIGN_KEY_CHECKS=1;\n");
                    sql.flush();
                    zip.closeEntry();
                    // 结构发生变化时拒绝发布；并发 DDL 仍须由发布规程禁止。
                    if (!tables.equals(mapper.schema(connection))) throw new BackupFailure(UNSUPPORTED_SCHEMA);
                    // 正式对象键不可由业务覆盖；清单固定后尽早释放读事务，减少长事务对清理旧版本的影响。
                    connection.rollback();
                    var stored = new ArrayList<BackupManifest.StoredObject>();
                    for (var reference : references) stored.add(copy(reference, zip, budget, stored.size()));
                    manifest = new BackupManifest(1, id, snapshotAt, HexFormat.of().formatHex(digest.digest()), counts, stored);
                    zip.putNextEntry(new ZipEntry("manifest.json"));
                    var bytes = json.writeValueAsBytes(manifest);
                    if (bytes.length > 8 * 1024 * 1024) throw new BackupFailure(LIMIT_EXCEEDED);
                    budget.consume(bytes.length);
                    zip.write(bytes);
                    zip.closeEntry();
                }
                connection.rollback();
                // 原子发布前仍不对外提供文件；相同编号不允许覆盖已有归档。
                try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                String checksum = sha256(temporary);
                BackupArchiveVerifier.verify(temporary, checksum, properties.maxUncompressedBytes(), json);
                budget.check();
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                temporary = null;
                return new Archive(id, target, Files.size(target), checksum, manifest);
            } finally {
                try {
                    if (!connection.getAutoCommit()) connection.rollback();
                    connection.setReadOnly(false);
                    connection.setAutoCommit(true);
                    try (var reset = connection.prepareStatement("SET time_zone = ?")) {
                        reset.setString(1, timezone); reset.execute();
                    }
                } finally {
                    try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                        release.setString(1, lock); release.executeQuery().close();
                    }
                }
            }
        } catch (BackupFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw new BackupFailure(STORAGE_FAILED, failure);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException failure) { /* 残留临时文件不具备成功状态，由运维清理。 */ }
            }
        }
    }

    private BackupManifest.StoredObject copy(ObjectRef reference, ZipOutputStream zip, Budget budget, int index) throws IOException {
        var digest = digest();
        String entry = "objects/" + String.format(Locale.ROOT, "%08d.bin", index);
        zip.putNextEntry(new ZipEntry(entry));
        long total = 0;
        try (var input = objects.openBackupObject(reference.bucket(), reference.key())) {
            var buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) != -1) {
                budget.consume(length); total += length;
                if (reference.size() >= 0 && total > reference.size()) throw new BackupFailure(INTEGRITY_FAILED);
                digest.update(buffer, 0, length); zip.write(buffer, 0, length);
            }
        }
        var hash = HexFormat.of().formatHex(digest.digest());
        if (reference.size() >= 0 && (total != reference.size() || !hash.equals(reference.sha256()))) throw new BackupFailure(INTEGRITY_FAILED);
        zip.closeEntry();
        return new BackupManifest.StoredObject(entry, reference.bucket(), reference.key(), reference.mediaType(), total, hash);
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("缺少 SHA-256 算法", failure); }
    }
    private static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    public static String sha256(Path path) throws IOException {
        return BackupChecksum.sha256(path);
    }
    private static class Budget {
        private final long maximum;
        private final long deadline;
        private long bytes;
        Budget(long maximum, int seconds) { this.maximum = maximum; this.deadline = System.nanoTime() + seconds * 1_000_000_000L; }
        void check() { if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new BackupFailure(LIMIT_EXCEEDED); }
        void consume(int amount) { check(); bytes += amount; if (bytes > maximum) throw new BackupFailure(LIMIT_EXCEEDED); }
    }
    private static class BudgetOutput extends OutputStream {
        private final OutputStream target;
        private final Budget budget;
        BudgetOutput(OutputStream target, Budget budget) { this.target = target; this.budget = budget; }
        @Override public void write(int value) throws IOException { budget.consume(1); target.write(value); }
        @Override public void write(byte[] data, int offset, int length) throws IOException { budget.consume(length); target.write(data, offset, length); }
    }
}
