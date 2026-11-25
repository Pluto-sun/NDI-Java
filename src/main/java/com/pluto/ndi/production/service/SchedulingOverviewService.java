package com.pluto.ndi.production.service;

import com.pluto.ndi.organization.service.RegionAdcodeCatalog;
import com.pluto.ndi.production.domain.SchedulingOverview;
import com.pluto.ndi.production.domain.SchedulingOverview.*;
import com.pluto.ndi.production.mapper.ProductionPlanMapper;
import com.pluto.ndi.revenue.RevenueSchedulingReport;
import com.pluto.ndi.shared.reporting.DashboardSummary.MonthRange;
import com.pluto.ndi.shared.reporting.SchedulingOverviewSource;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Stream;

@Service
public class SchedulingOverviewService {
    private static final List<String> CATEGORIES = List.of("网络化", "数字化", "智能化");
    private static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    private final ProductionAccessService productionAccess;
    private final ProductionPlanMapper production;
    private final RevenueSchedulingReport revenue;
    private final RegionAdcodeCatalog adcodes;

    public SchedulingOverviewService(ProductionAccessService productionAccess, ProductionPlanMapper production,
            RevenueSchedulingReport revenue, RegionAdcodeCatalog adcodes) {
        this.productionAccess = productionAccess;
        this.production = production;
        this.revenue = revenue;
        this.adcodes = adcodes;
    }

    @Transactional(readOnly = true)
    public SchedulingOverview load(UUID actor, String from, String to) {
        var planScope = productionAccess.require(actor);
        var range = MonthRange.parse(from, to);
        if (range.from() == null || range.to() == null) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var start = YearMonth.from(LocalDate.parse(range.from()));
        var finish = YearMonth.from(LocalDate.parse(range.to()));
        var revenueReport = revenue.load(actor, range);
        var recognized = revenueReport.source();
        var plan = production.schedulingOverview(planScope, range);
        var totals = new Aggregates();
        totals.add(plan, false);
        totals.add(recognized, true);
        var timeline = Stream.iterate(start, month -> !month.isAfter(finish), month -> month.plusMonths(1)).toList();
        var recent = timeline.subList(Math.max(0, timeline.size() - 3), timeline.size());
        var months = new ArrayList<Month>();
        Totals previous = null;
        for (var month : timeline) {
            var current = totals.months.getOrDefault(month, ZERO);
            months.add(new Month(month.toString(), current.view(),
                    previous == null ? null : decimal(current.planned.subtract(previous.planned)),
                    previous == null ? null : decimal(current.recognized.subtract(previous.recognized))));
            previous = current;
        }
        var provinces = totals.provinces.entrySet().stream()
                .sorted(Map.Entry.<Region, RegionTotals>comparingByValue(
                        Comparator.comparing((RegionTotals item) -> item.total.recognized).reversed())
                        .thenComparing(entry -> entry.getKey().name).thenComparing(entry -> entry.getKey().key))
                .map(entry -> {
                    var region = entry.getKey();
                    var values = entry.getValue();
                    return new Province(region.key, region.name, region.province ? adcodes.province(region.name) : null,
                            region.province, values.total.view(),
                            recent.stream().map(month -> values.months.getOrDefault(month, ZERO).view()).toList());
                }).toList();
        var categories = CATEGORIES.stream()
                .map(name -> new Category(name, totals.categories.getOrDefault(name, ZERO).view())).toList();
        return new SchedulingOverview(plan.projectCount(), recognized.projectCount(), totals.total.view(),
                planScope.scope().level().name(), revenueReport.national() ? "NATIONAL" : "PROVINCE",
                plan.groups().isEmpty() && recognized.groups().isEmpty(), categories, List.copyOf(months), provinces);
    }

    private static String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private record Totals(BigDecimal planned, BigDecimal recognized) {
        Totals plus(Totals other) { return new Totals(planned.add(other.planned), recognized.add(other.recognized)); }
        Amounts view() {
            return new Amounts(decimal(planned), decimal(recognized), decimal(planned.subtract(recognized)),
                    planned.signum() == 0 ? null : decimal(recognized.movePointRight(2).divide(planned, 4, RoundingMode.HALF_UP)));
        }
    }

    private record Region(String key, String name, boolean province) {}

    private static final class RegionTotals {
        private Totals total = ZERO;
        private final Map<YearMonth, Totals> months = new HashMap<>();
    }

    private static final class Aggregates {
        private Totals total = ZERO;
        private final Map<String, Totals> categories = new HashMap<>();
        private final Map<YearMonth, Totals> months = new HashMap<>();
        private final Map<Region, RegionTotals> provinces = new HashMap<>();

        void add(SchedulingOverviewSource source, boolean recognized) {
            for (var row : source.groups()) {
                var month = YearMonth.from(row.month());
                var region = new Region(row.provinceId() == null ? row.provinceName() : row.provinceId(),
                        row.provinceName(), row.provinceId() != null);
                var province = provinces.computeIfAbsent(region, key -> new RegionTotals());
                row.amounts().forEach((category, amount) -> {
                    var change = recognized ? new Totals(BigDecimal.ZERO, amount) : new Totals(amount, BigDecimal.ZERO);
                    total = total.plus(change);
                    province.total = province.total.plus(change);
                    months.merge(month, change, Totals::plus);
                    province.months.merge(month, change, Totals::plus);
                    categories.merge(category, change, Totals::plus);
                });
            }
        }
    }
}
