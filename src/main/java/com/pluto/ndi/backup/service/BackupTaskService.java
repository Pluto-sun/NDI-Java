package com.pluto.ndi.backup.service;

import com.pluto.ndi.backup.config.BackupProperties;
import com.pluto.ndi.backup.domain.*;
import com.pluto.ndi.backup.mapper.BackupTaskMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.pluto.ndi.backup.domain.BackupErrorCode.*;

@Service
public class BackupTaskService {
    private final BackupTaskMapper mapper;
    private final IdentityDirectory identities;
    private final BackupProperties properties;
    private final AuditWriter audit;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public BackupTaskService(BackupTaskMapper mapper, IdentityDirectory identities, BackupProperties properties, AuditWriter audit, Clock clock, PlatformTransactionManager manager) {
        this.mapper = mapper; this.identities = identities; this.properties = properties; this.audit = audit; this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }
    @Transactional
    public BackupTaskSummary create(UUID actor, UUID requestId) {
        requireAdministrator(actor);
        if (!properties.enabled()) throw new BusinessException(DISABLED);
        var existing = mapper.find(requestId);
        if (existing.isPresent()) return existing(actor, existing.get());
        try { mapper.insert(requestId, actor, Instant.now(clock)); }
        catch (DuplicateKeyException duplicate) {
            // 同一请求的并发重试返回原单；另一份活动任务则明确返回冲突。
            var duplicateRequest = mapper.findCurrent(requestId);
            if (duplicateRequest.isPresent()) return existing(actor, duplicateRequest.get());
            throw new BusinessException(BUSY);
        }
        audit.record(actor, "BACKUP", requestId, "BACKUP_REQUESTED", Map.of());
        return BackupTaskSummary.from(mapper.find(requestId).orElseThrow());
    }
    private BackupTaskSummary existing(UUID actor, BackupTask task) {
        if (!actor.equals(task.requestedBy())) throw new BusinessException(REQUEST_CONFLICT);
        return BackupTaskSummary.from(task);
    }
    @Transactional(readOnly = true)
    public Page<BackupTaskSummary> list(UUID actor, Pageable page) {
        requireAdministrator(actor);
        return mapper.list(page).map(BackupTaskSummary::from);
    }
    @Transactional(readOnly = true)
    public BackupTaskSummary find(UUID actor, UUID id) {
        requireAdministrator(actor); return BackupTaskSummary.from(requireTask(id));
    }
    public boolean enabled() { return properties.enabled(); }

    // 以下工作接口仅由持有数据库会话工作锁的 Worker 调用，不开放 HTTP 入口。
    @Transactional
    public void recoverInterrupted() {
        for (var task : mapper.runningLocked()) {
            if (mapper.fail(task.id(), task.workerToken(), "INTERRUPTED", Instant.now(clock)) > 0) {
                audit.record(null, "BACKUP", task.id(), "BACKUP_FAILED", Map.of("reason", "INTERRUPTED"));
            }
        }
    }
    @Transactional
    public Optional<BackupTask> claim() {
        var pending = mapper.pendingLocked();
        if (pending.isEmpty()) return Optional.empty();
        var task = pending.get(); var token = UUID.randomUUID();
        mapper.start(task.id(), token, Instant.now(clock));
        if (!identities.isSystemAdministrator(task.requestedBy())) {
            mapper.fail(task.id(), token, "ACTOR_REVOKED", Instant.now(clock));
            audit.record(null, "BACKUP", task.id(), "BACKUP_FAILED", Map.of("reason", "ACTOR_REVOKED"));
            return Optional.empty();
        }
        audit.record(null, "BACKUP", task.id(), "BACKUP_STARTED", Map.of());
        return mapper.find(task.id());
    }
    @Transactional
    public void complete(BackupTask task, BackupArchiveService.Archive archive) {
        if (!task.id().equals(archive.id())) throw new IllegalArgumentException("备份结果编号不匹配");
        if (mapper.finish(task.id(), task.workerToken(), archive, Instant.now(clock)) > 0) {
            audit.record(null, "BACKUP", task.id(), "BACKUP_SUCCEEDED", Map.of("sha256", archive.sha256(), "bytes", archive.bytes()));
        }
    }
    @Transactional
    public void fail(BackupTask task, String code) {
        if (mapper.fail(task.id(), task.workerToken(), code, Instant.now(clock)) > 0) {
            audit.record(null, "BACKUP", task.id(), "BACKUP_FAILED", Map.of("reason", code));
        }
    }

    public record Download(InputStream stream, long bytes, String sha256, String filename) implements AutoCloseable {
        public void close() throws IOException { stream.close(); }
    }
    public Download openDownload(UUID actor, UUID id) {
        return openVerified(actor, id, true);
    }
    public void checkDownload(UUID actor, UUID id) {
        try (var download = openVerified(actor, id, false)) { }
        catch (IOException failure) { throw new BusinessException(UNAVAILABLE); }
    }
    private Download openVerified(UUID actor, UUID id, boolean recordIntent) {
        requireAdministrator(actor);
        var task = requireTask(id);
        if (!"SUCCEEDED".equals(task.status())) throw new BusinessException(NOT_READY);
        FileChannel channel = null;
        try {
            var root = properties.directory().toAbsolutePath().normalize();
            var path = root.resolve(id + ".zip");
            if (Files.isSymbolicLink(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new BusinessException(UNAVAILABLE);
            channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            if (task.archiveBytes() == null || channel.size() != task.archiveBytes()) throw new BusinessException(UNAVAILABLE);
            var digest = MessageDigest.getInstance("SHA-256");
            var input = new DigestInputStream(Channels.newInputStream(channel), digest);
            input.transferTo(OutputStream.nullOutputStream());
            if (!HexFormat.of().formatHex(digest.digest()).equals(task.sha256())) throw new BusinessException(UNAVAILABLE);
            channel.position(0);
            // 归档校验不占用长事务；审计提交失败时仍由本方法关闭已打开文件。
            if (recordIntent) transactions.executeWithoutResult(status -> {
                    requireAdministrator(actor);
                    audit.record(actor, "BACKUP", id, "BACKUP_DOWNLOAD_INTENT", Map.of("sha256", task.sha256()));
                });
            return new Download(Channels.newInputStream(channel), task.archiveBytes(), task.sha256(), "ndi-backup-" + id + ".zip");
        } catch (Exception failure) {
            if (channel != null) try { channel.close(); } catch (IOException ignored) { }
            if (failure instanceof BusinessException business) throw business;
            throw new BusinessException(UNAVAILABLE);
        }
    }
    private BackupTask requireTask(UUID id) { return mapper.find(id).orElseThrow(() -> new BusinessException(NOT_FOUND)); }
    private void requireAdministrator(UUID actor) {
        if (!identities.isSystemAdministrator(actor)) throw new BusinessException(FORBIDDEN);
    }
}
