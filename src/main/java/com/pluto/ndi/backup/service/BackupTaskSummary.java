package com.pluto.ndi.backup.service;

import com.pluto.ndi.backup.domain.BackupTask;
import java.time.Instant;
import java.util.UUID;

/** 管理端摘要不暴露工作令牌或文件系统路径。 */
public record BackupTaskSummary(UUID id, String backupCode, String status, Instant requestedAt,
        Instant startedAt, Instant finishedAt, Long archiveBytes, String sha256, Instant snapshotAt,
        Integer tableCount, Integer objectCount, String errorCode) {
    public static BackupTaskSummary from(BackupTask task) {
        return new BackupTaskSummary(task.id(), "BKP-" + task.id(), task.status(), task.requestedAt(), task.startedAt(),
                task.finishedAt(), task.archiveBytes(), task.sha256(), task.snapshotAt(), task.tableCount(), task.objectCount(), task.errorCode());
    }
}
