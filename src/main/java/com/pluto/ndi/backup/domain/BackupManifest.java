package com.pluto.ndi.backup.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 归档格式独立版本化；清单不保存连接地址、凭据或宿主机路径。 */
public record BackupManifest(int formatVersion, UUID backupId, Instant snapshotAt,
        String databaseSha256, List<TableCount> tables, List<StoredObject> objects) {
    public record TableCount(String name, long rows) { }
    public record StoredObject(String entry, String bucket, String objectKey, String mediaType,
            long size, String sha256) { }
}
