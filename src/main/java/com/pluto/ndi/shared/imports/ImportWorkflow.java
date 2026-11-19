package com.pluto.ndi.shared.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.shared.excel.ExcelImportLimits;

import java.util.*;

/** 项目、排产、计收共享的步骤、必填映射和选择规则。 */
public final class ImportWorkflow {
    private ImportWorkflow() {}

    public static boolean validStep(int previous, int next) {
        return next >= 1 && next <= 3 && Math.abs(previous - next) <= 1;
    }

    public static boolean validSelection(List<Integer> selection) {
        return selection == null
                || selection.size() <= ExcelImportLimits.MAX_ROWS
                        && new HashSet<>(selection).size() == selection.size()
                        && selection.stream().allMatch(n -> n != null && n > 0);
    }

    public static List<String> missing(Map<String, String> mapping, Collection<String> required) {
        return required.stream()
                .filter(f -> mapping.get(f) == null || mapping.get(f).isBlank())
                .sorted()
                .toList();
    }

    /** 空值或旧格式表示缺少可用明细，由调用方按当前预检规则补算。 */
    public static List<String> failureReasons(ObjectMapper json, String encoded) {
        if (encoded == null) return List.of();
        try {
            var value = json.readTree(encoded);
            if (value == null || !value.isArray()) return List.of();
            var reasons = new LinkedHashSet<String>();
            for (var item : value) {
                if (item.isTextual() && !item.textValue().isBlank()) reasons.add(item.textValue());
            }
            return List.copyOf(reasons);
        } catch (JsonProcessingException failure) {
            return List.of();
        }
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
