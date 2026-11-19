package com.pluto.ndi.shared.reporting;

import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.CommonErrorCode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

public record DashboardSummary(long count, String amount, List<Metric> categories, List<Metric> provinces) {
    public record Metric(String label, long count, String amount) {
        public Metric(String label, long count, BigDecimal amount) {
            this(label, count, amount == null ? null : amount.stripTrailingZeros().toPlainString());
        }
    }

    public record MonthRange(String from, String to) {
        public static MonthRange parse(String from, String to) {
            try {
                var start = month(from);
                var end = month(to);
                if (start != null && end != null && start.compareTo(end) > 0) throw new IllegalArgumentException();
                return new MonthRange(start, end);
            } catch (IllegalArgumentException | java.time.DateTimeException e) {
                throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
            }
        }

        private static String month(String value) {
            if (value == null || value.isBlank()) return null;
            if (!value.matches("[0-9]{4}-[0-9]{2}") || YearMonth.parse(value).getYear() < 1900)
                throw new IllegalArgumentException();
            return value + "-01";
        }
    }

    public static DashboardSummary quantities(List<Metric> provinces) {
        return new DashboardSummary(provinces.stream().mapToLong(Metric::count).sum(), null, List.of(), provinces);
    }

    public static DashboardSummary amounts(List<Metric> categories, List<Metric> provinces) {
        var ordered = provinces.stream().sorted(Comparator
                .comparing((Metric metric) -> new BigDecimal(metric.amount())).reversed()
                .thenComparing(Metric::label)).toList();
        return new DashboardSummary(ordered.stream().mapToLong(Metric::count).sum(),
                ordered.stream().map(Metric::amount).map(BigDecimal::new)
                        .reduce(BigDecimal.ZERO, BigDecimal::add).stripTrailingZeros().toPlainString(),
                categories, ordered);
    }
}
