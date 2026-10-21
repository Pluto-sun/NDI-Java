package com.pluto.ndi.backup.domain;

import java.time.Instant;
import java.util.UUID;

/** 任务记录不包含宿主机绝对路径、数据库连接信息或存储凭据。 */
public record BackupTask(UUID id, UUID requestedBy, String status, Instant requestedAt, Instant startedAt,
        Instant finishedAt, UUID workerToken, Long archiveBytes, String sha256, Instant snapshotAt,
        Integer tableCount, Integer objectCount, String errorCode) { }
