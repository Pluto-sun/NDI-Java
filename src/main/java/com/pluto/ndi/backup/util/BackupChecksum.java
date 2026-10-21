package com.pluto.ndi.backup.util;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** 流式计算归档摘要，不将归档整体载入内存。 */
public final class BackupChecksum {
    private BackupChecksum() { }
    public static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(path), digest)) { input.transferTo(OutputStream.nullOutputStream()); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("缺少 SHA-256 算法", failure); }
    }
}
