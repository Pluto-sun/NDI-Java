package com.pluto.ndi.identity.util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

public final class SessionTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private SessionTokens() { }
    public static String generate() {
        byte[] value = new byte[32]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    public static boolean valid(String token) { return token != null && token.matches("[A-Za-z0-9_-]{43}"); }
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("会话摘要算法不可用", exception); }
    }
}
