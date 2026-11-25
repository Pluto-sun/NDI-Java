package com.pluto.ndi.revenue.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/** 统一全半角和首尾空格，保留组织名称内部的空格、大小写和标点。 */
public final class RevenueOrganizationNames {
    private static final Pattern WIDTH = Pattern.compile("[\\u3000\\uFF01-\\uFFEF]+");

    private RevenueOrganizationNames() {}

    public static byte[] key(String name) {
        var normalized = WIDTH.matcher(Objects.toString(name, ""))
                .replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                        Normalizer.normalize(match.group(), Normalizer.Form.NFKC))).strip();
        if (normalized.isEmpty()) return null;
        try {
            return MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
