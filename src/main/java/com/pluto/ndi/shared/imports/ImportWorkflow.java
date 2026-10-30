package com.pluto.ndi.shared.imports;

import java.util.*;

/** 项目、排产、计收共享的步骤、必填映射和选择规则。 */
public final class ImportWorkflow {
    private ImportWorkflow() {}

    public static boolean validStep(int previous, int next) {
        return next >= 1 && next <= 3 && Math.abs(previous - next) <= 1;
    }

    public static boolean validSelection(List<Integer> selection) {
        return selection == null
                || selection.size() <= 1000
                        && new HashSet<>(selection).size() == selection.size()
                        && selection.stream().allMatch(n -> n != null && n > 0);
    }

    public static List<String> missing(Map<String, String> mapping, Collection<String> required) {
        return required.stream()
                .filter(f -> mapping.get(f) == null || mapping.get(f).isBlank())
                .sorted()
                .toList();
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
