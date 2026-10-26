package com.pluto.ndi.projects.util;

import java.text.Normalizer;
import java.util.Locale;

/** 名称展示原文不变，比较时统一字符、标点、数字及边界通用词。 */
public final class ProjectNameMatcher {
    private static final String GENERIC = "项目|工程|系统|平台|建设";

    private ProjectNameMatcher() {}

    public static String standard(String value) {
        String input =
                Normalizer.normalize(value.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        var digits = new StringBuilder();
        String chinese = "零一二三四五六七八九";
        var matcher = java.util.regex.Pattern.compile("[零〇一二三四五六七八九十百千万亿两]+").matcher(input);
        int start = 0;
        while (matcher.find()) {
            digits.append(input, start, matcher.start());
            String run = matcher.group();
            // 按位年份转换；带十、百等单位的数量词整体保留。
            if (run.matches(".*[十百千万亿两].*")) digits.append(run);
            else for (char c : run.toCharArray()) digits.append(c == '〇' ? 0 : chinese.indexOf(c));
            start = matcher.end();
        }
        digits.append(input, start, input.length());
        // 保留字母与数字，统一中文标点、空格及全半角差异。
        var normalized = new StringBuilder();
        for (int offset = 0; offset < digits.length(); ) {
            int character = digits.codePointAt(offset);
            if (Character.isLetterOrDigit(character)) normalized.appendCodePoint(character);
            else if (character == '.'
                    && offset > 0
                    && offset + 1 < digits.length()
                    && Character.isDigit(digits.charAt(offset - 1))
                    && Character.isDigit(digits.charAt(offset + 1))) normalized.append('.');
            offset += Character.charCount(character);
        }
        return normalized.toString();
    }

    public static String core(String value) {
        var standard = standard(value);
        var result = standard;
        String previous;
        do {
            previous = result;
            result = result.replaceAll("^(?:" + GENERIC + ")|(?:" + GENERIC + ")$", "");
        } while (!previous.equals(result));
        // 纯通用名称不能同时归一为空后被误判为同名。
        return result.isEmpty() ? standard : result;
    }

    public static int similarity(String first, String second) {
        return (int) Math.round(score(first, second) * 100);
    }

    public static double score(String first, String second) {
        return scoreCore(core(first), core(second));
    }

    public static double scoreCore(String a, String b) {
        if (a.equals(b)) return a.isEmpty() ? 0 : 1;
        if (a.isEmpty() || b.isEmpty()) return 0;
        return (jaroWinkler(a, b) + editSimilarity(a, b)) / 2;
    }

    private static double jaroWinkler(String a, String b) {
        if (a.length() < b.length() || a.length() == b.length() && a.compareTo(b) < 0) {
            var swap = a;
            a = b;
            b = swap;
        }
        int range = Math.max(0, Math.max(a.length(), b.length()) / 2 - 1), matches = 0;
        boolean[] left = new boolean[a.length()], right = new boolean[b.length()];
        for (int i = 0; i < a.length(); i++)
            for (int j = Math.max(0, i - range); j < Math.min(b.length(), i + range + 1); j++)
                if (!right[j] && a.charAt(i) == b.charAt(j)) {
                    left[i] = true;
                    right[j] = true;
                    matches++;
                    break;
                }
        if (matches == 0) return 0;
        int transposed = 0, j = 0;
        for (int i = 0; i < a.length(); i++)
            if (left[i]) {
                while (!right[j]) j++;
                if (a.charAt(i) != b.charAt(j)) transposed++;
                j++;
            }
        double m = matches,
                jaro = (m / a.length() + m / b.length() + (m - transposed / 2.0) / m) / 3;
        int prefix = 0;
        while (prefix < Math.min(4, Math.min(a.length(), b.length()))
                && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        return jaro > 0.7 ? jaro + prefix * 0.1 * (1 - jaro) : jaro;
    }

    private static double editSimilarity(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j < previous.length; j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++)
                current[j] =
                        Math.min(
                                Math.min(current[j - 1] + 1, previous[j] + 1),
                                previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return 1.0 - (double) previous[b.length()] / Math.max(a.length(), b.length());
    }
}
