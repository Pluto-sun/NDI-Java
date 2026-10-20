package com.pluto.ndi.projects.util;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 统一在线新建与 Excel 导入的项目名称比对口径。 */
public final class ProjectNameMatcher {
    private static final String[] GENERIC_WORDS = {
            "项目", "建设", "系统", "平台", "智慧工厂", "工业互联网", "5g", "数字化", "升级"
    };

    private ProjectNameMatcher() { }

    public static String standard(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-—_，,。\\.、/\\\\()（）【】\\[\\]{}]+", "");
    }

    public static int similarity(String first, String second) {
        var left = standard(first);
        var right = standard(second);
        var edit = editSimilarity(left, right);
        var leftPairs = bigrams(core(left));
        var rightPairs = bigrams(core(right));
        var union = new HashSet<>(leftPairs);
        union.addAll(rightPairs);
        var intersection = new HashSet<>(leftPairs);
        intersection.retainAll(rightPairs);
        var pairScore = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
        return (int) Math.round((edit + pairScore) * 50);
    }

    private static String core(String standard) {
        var result = standard;
        for (var word : GENERIC_WORDS) result = result.replace(word, "");
        return result;
    }

    private static Set<String> bigrams(String value) {
        var result = new HashSet<String>();
        for (int i = 0; i < value.length() - 1; i++) result.add(value.substring(i, i + 2));
        if (result.isEmpty() && !value.isEmpty()) result.add(value);
        return result;
    }

    private static double editSimilarity(String left, String right) {
        if (left.equals(right)) return 1;
        if (left.isEmpty() || right.isEmpty()) return 0;
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index < previous.length; index++) previous[index] = index;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return 1.0 - (double) previous[right.length()] / Math.max(left.length(), right.length());
    }
}
