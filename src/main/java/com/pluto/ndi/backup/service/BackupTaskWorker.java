package com.pluto.ndi.backup.service;

import com.pluto.ndi.backup.config.BackupProperties;
import com.pluto.ndi.backup.domain.BackupFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 工作锁覆盖恢复、领取和整个归档过程；进程退出后由 MySQL 释放，不靠文件判断活跃性。 */
@Component
@ConditionalOnProperty(prefix = "ndi.backup", name = "enabled", havingValue = "true")
public class BackupTaskWorker {
    private static final Logger LOG = LoggerFactory.getLogger(BackupTaskWorker.class);
    private final DataSource source;
    private final BackupTaskService tasks;
    private final BackupArchiveService archives;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("ndi-backup-worker").daemon(true).factory());
    private final AtomicBoolean dispatched = new AtomicBoolean();
    public BackupTaskWorker(DataSource source, BackupTaskService tasks, BackupArchiveService archives) {
        this.source = source; this.tasks = tasks; this.archives = archives;
    }
    @Scheduled(fixedDelayString = "${ndi.backup.poll-delay-ms:5000}", initialDelayString = "${ndi.backup.initial-delay-ms:5000}")
    public void dispatch() {
        // 共享定时调度线程只派发，不让长归档阻塞审批、导出等业务扫描。
        if (!dispatched.compareAndSet(false, true)) return;
        try { executor.execute(() -> { try { runPending(); } finally { dispatched.set(false); } }); }
        catch (RejectedExecutionException stopped) { dispatched.set(false); }
    }
    @PreDestroy
    public void stop() { executor.shutdownNow(); }

    public void runPending() {
        try (var connection = source.getConnection()) {
            var hash = MessageDigest.getInstance("SHA-256").digest(connection.getCatalog().getBytes(StandardCharsets.UTF_8));
            String lock = "ndi.backup.worker." + HexFormat.of().formatHex(hash).substring(0, 32);
            try (var query = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
                query.setString(1, lock);
                try (var result = query.executeQuery()) { if (!result.next() || result.getInt(1) != 1) return; }
            }
            try {
                tasks.recoverInterrupted();
                tasks.claim().ifPresent(task -> {
                    try { tasks.complete(task, archives.create(task.id())); }
                    catch (RuntimeException failure) {
                        String code = failure instanceof BackupFailure backup ? backup.reason().name() : "INTERNAL_ERROR";
                        LOG.warn("备份任务失败，编号：{}，原因：{}", task.id(), code);
                        tasks.fail(task, code);
                    }
                });
            } finally {
                try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) { release.setString(1, lock); release.executeQuery().close(); }
            }
        } catch (Exception failure) { LOG.error("备份工作进程运行失败", failure); }
    }
}
