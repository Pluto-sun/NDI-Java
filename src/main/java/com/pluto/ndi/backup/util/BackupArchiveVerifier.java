package com.pluto.ndi.backup.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.backup.domain.BackupFailure;
import com.pluto.ndi.backup.domain.BackupManifest;
import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static com.pluto.ndi.backup.domain.BackupFailure.Reason.*;

/** 校验归档而不解压、不执行 SQL；外部摘要必须来自受信任的任务记录。 */
public final class BackupArchiveVerifier {
    private BackupArchiveVerifier() { }

    public static BackupManifest verify(Path archive, String expectedSha256, long maximumBytes, ObjectMapper json) {
        try {
            if (expectedSha256 == null || !expectedSha256.equals(BackupChecksum.sha256(archive))) throw new BackupFailure(INTEGRITY_FAILED);
            try (var zip = new ZipFile(archive.toFile())) {
                var entry = zip.getEntry("manifest.json");
                if (entry == null) throw new BackupFailure(INTEGRITY_FAILED);
                byte[] content;
                try (var input = zip.getInputStream(entry)) { content = input.readNBytes(8 * 1024 * 1024 + 1); }
                if (content.length > 8 * 1024 * 1024) throw new BackupFailure(LIMIT_EXCEEDED);
                var manifest = json.readValue(content, BackupManifest.class);
                if (manifest.formatVersion() != 1 || manifest.backupId() == null || manifest.snapshotAt() == null
                        || manifest.tables() == null || manifest.objects() == null || manifest.objects().size() > 100000) throw new BackupFailure(INTEGRITY_FAILED);
                var expected = new HashSet<>(List.of("database.sql", "manifest.json"));
                var keys = new HashSet<String>();
                long remaining = maximumBytes - content.length;
                remaining -= verifyEntry(zip, "database.sql", -1, manifest.databaseSha256(), remaining);
                int index = 0;
                for (var object : manifest.objects()) {
                    String name = "objects/" + String.format(Locale.ROOT, "%08d.bin", index++);
                    if (!name.equals(object.entry()) || object.size() < 0 || object.bucket() == null || object.objectKey() == null
                            || !keys.add(object.bucket() + "\0" + object.objectKey())) throw new BackupFailure(INTEGRITY_FAILED);
                    expected.add(name);
                    remaining -= verifyEntry(zip, name, object.size(), object.sha256(), remaining);
                }
                var entries = zip.entries();
                while (entries.hasMoreElements()) if (!expected.remove(entries.nextElement().getName())) throw new BackupFailure(INTEGRITY_FAILED);
                if (!expected.isEmpty()) throw new BackupFailure(INTEGRITY_FAILED);
                return manifest;
            }
        } catch (BackupFailure failure) { throw failure; }
        catch (Exception failure) { throw new BackupFailure(INTEGRITY_FAILED, failure); }
    }

    private static long verifyEntry(ZipFile zip, String name, long expectedSize, String hash, long remaining) throws Exception {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new BackupFailure(INTEGRITY_FAILED);
        var entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) throw new BackupFailure(INTEGRITY_FAILED);
        var digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (var input = zip.getInputStream(entry)) {
            var buffer = new byte[65536]; int length;
            while ((length = input.read(buffer)) != -1) {
                size += length;
                if (size > remaining) throw new BackupFailure(LIMIT_EXCEEDED);
                digest.update(buffer, 0, length);
            }
        }
        if (expectedSize >= 0 && size != expectedSize || !hash.equals(HexFormat.of().formatHex(digest.digest()))) throw new BackupFailure(INTEGRITY_FAILED);
        return size;
    }
}
