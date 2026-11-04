package com.pluto.ndi.revenue.util;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Pattern;

/** 条款标识和审签日期按来源字段语义归一，不改变其他业务标识。 */
public final class RevenueSourceNormalizer {
    private static final Pattern DATE_TIME =
            Pattern.compile(
                    "^(\\d{4})([-/])(\\d{1,2})\\2(\\d{1,2})(?:["
                        + " T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2})(?:\\.(\\d{1,9}))?)?(Z|[+-]\\d{2}:?\\d{2})?)?$");

    private RevenueSourceNormalizer() {}

    public static String clauseNumber(String value) {
        value = Objects.toString(value, "").strip();
        if (value.equals("--")) return "";
        return value.matches("[0-9]+") ? value.replaceFirst("^0+(?!$)", "") : value;
    }

    /** 校验完整时间后保留来源日历日期，不做时区换算。 */
    public static String signatureDate(String input) {
        String value = Objects.toString(input, "").strip().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.equals("--")) return "";
        var match = DATE_TIME.matcher(value);
        try {
            if (!match.matches()) throw new DateTimeException("日期格式无效");
            var date =
                    LocalDate.of(
                            Integer.parseInt(match.group(1)),
                            Integer.parseInt(match.group(3)),
                            Integer.parseInt(match.group(4)));
            if (match.group(5) != null) {
                int seconds = match.group(7) == null ? 0 : Integer.parseInt(match.group(7));
                int nanos =
                        match.group(8) == null
                                ? 0
                                : Integer.parseInt((match.group(8) + "000000000").substring(0, 9));
                LocalTime.of(
                        Integer.parseInt(match.group(5)),
                        Integer.parseInt(match.group(6)),
                        seconds,
                        nanos);
                if (match.group(9) != null) ZoneOffset.of(match.group(9));
            }
            return date.toString();
        } catch (DateTimeException | NumberFormatException invalid) {
            throw RevenueFields.invalid(
                    "审签日期须为有效日期或日期时间，例如2026-07-20、2026/7/20 0:00:00；无日期请留空或填--");
        }
    }
}
