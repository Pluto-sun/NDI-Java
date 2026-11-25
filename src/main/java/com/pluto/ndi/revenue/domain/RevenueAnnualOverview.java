package com.pluto.ndi.revenue.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.List;

/** API 金额单位为万元。 */
public record RevenueAnnualOverview(int year, int currentMonth, boolean nationalRevenue, String projectScope,
        int coveredProvinces, Money total, List<Month> months, List<Region> regions, Gewu gewu, List<Conflict> conflicts) {
    public record Money(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal network,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal digital,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal intelligent,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {}
    public record Month(int month, boolean occurred, Money money) {}
    public record Region(String key, String name, String adcode, boolean geographic, boolean organization,
            boolean hasRevenue, List<Month> months, Money total, Gewu gewu) {}
    public record Gewu(long signedCount, long recognizedCount, long countGap,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal signedAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal recognizedAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountGap,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal countGapRate,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountGapRate) {}
    public record Conflict(String contract, List<String> regions) {}
}
