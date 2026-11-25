package com.pluto.ndi.revenue.service;

import com.pluto.ndi.organization.service.RegionAdcodeCatalog;
import com.pluto.ndi.projects.GewuContractReport;
import com.pluto.ndi.revenue.domain.RevenueAnnualOverview;
import com.pluto.ndi.revenue.domain.RevenueAnnualOverview.*;
import com.pluto.ndi.revenue.mapper.RevenueMapper;
import com.pluto.ndi.revenue.mapper.RevenueMapper.AnnualRecognition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.IntStream;

@Service
public class RevenueAnnualOverviewService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<String> ORGANIZATIONS = Set.of("数科", "数智");
    private static final Bucket UNASSIGNED = new Bucket("UNASSIGNED", "未填写集成归属", false, false);
    private final RevenueAccessService access;
    private final RevenueMapper revenue;
    private final GewuContractReport projects;
    private final RegionAdcodeCatalog adcodes;
    private final Clock clock;

    public RevenueAnnualOverviewService(RevenueAccessService access, RevenueMapper revenue,
            GewuContractReport projects, RegionAdcodeCatalog adcodes, Clock clock) {
        this.access = access;
        this.revenue = revenue;
        this.projects = projects;
        this.adcodes = adcodes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RevenueAnnualOverview load(UUID actor) {
        var now = YearMonth.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        var scope = access.require(actor);
        var signed = projects.load(actor, now.getYear());
        var matches = new HashMap<String, Set<Bucket>>();
        for (var project : signed.contracts()) {
            if (!key(project.number()).isEmpty()) matches.putIfAbsent(key(project.number()), new HashSet<>());
        }
        var grouped = new HashMap<Bucket, Totals>();
        var total = new Totals();
        for (var row : revenue.annualRecognitions(scope, now.getYear())) {
            var bucket = bucket(row);
            var region = grouped.computeIfAbsent(bucket, ignored -> new Totals());
            region.add(row);
            total.add(row);
            var contract = matches.get(key(row.contract()));
            if (contract != null && "数字化".equals(row.category())) {
                contract.add(bucket);
                region.recognize(row);
                total.recognize(row);
            }
        }
        var conflicts = new ArrayList<Conflict>();
        for (var project : signed.contracts()) {
            var contract = key(project.number());
            var buckets = matches.getOrDefault(contract, Set.of());
            var region = new Bucket(project.provinceId(), project.provinceName(), true, false);
            if (buckets.size() == 1) region = buckets.iterator().next();
            if (buckets.size() > 1) {
                // 冲突时签约侧仅计入主归属，计收侧继续按各条生效认定归集。
                conflicts.add(new Conflict(contract, buckets.stream().map(Bucket::name).sorted().toList()));
            }
            grouped.computeIfAbsent(region, ignored -> new Totals()).sign(project.amount());
            total.sign(project.amount());
        }
        int current = now.getMonthValue();
        var rows = grouped.entrySet().stream().map(entry -> {
            var bucket = entry.getKey();
            var values = entry.getValue();
            return new Region(bucket.key(), bucket.name(),
                    bucket.geographic() ? adcodes.province(bucket.name()) : null,
                    bucket.geographic(), bucket.organization(), values.hasRevenue,
                    values.months(current), values.total(), bucket.organization() ? null : values.gewu());
        }).sorted(Comparator.comparing((Region row) -> row.total().total()).reversed()
                .thenComparing(Region::name).thenComparing(Region::key)).toList();
        int coverage = (int) rows.stream().filter(row -> row.geographic() && row.hasRevenue()).count();
        conflicts.sort(Comparator.comparing(Conflict::contract));
        return new RevenueAnnualOverview(now.getYear(), current, scope.national(), signed.scope().name(), coverage,
                total.total(), total.months(current), rows, total.gewu(), List.copyOf(conflicts));
    }

    private static Bucket bucket(AnnualRecognition row) {
        if (row.organization() != null && ORGANIZATIONS.contains(row.organization())) {
            return new Bucket(row.organization(), row.organization(), false, true);
        }
        return row.provinceId() == null ? UNASSIGNED
                : new Bucket(row.provinceId(), row.provinceName(), true, false);
    }

    private static String key(String text) {
        var value = Objects.toString(text, "").strip();
        return "--".equals(value) ? "" : value;
    }

    private static BigDecimal rate(BigDecimal gap, BigDecimal signed) {
        return signed.signum() == 0 ? null : gap.movePointRight(2).divide(signed, 4, RoundingMode.HALF_UP);
    }

    private record Bucket(String key, String name, boolean geographic, boolean organization) {}

    private static final class MoneyTotals {
        private BigDecimal network = ZERO;
        private BigDecimal digital = ZERO;
        private BigDecimal intelligent = ZERO;
        private BigDecimal total = ZERO;

        void add(AnnualRecognition row) {
            total = total.add(row.amount());
            switch (Objects.toString(row.category(), "")) {
                case "网络化" -> network = network.add(row.amount());
                case "数字化" -> digital = digital.add(row.amount());
                case "智能化" -> intelligent = intelligent.add(row.amount());
                default -> { } // 保留历史空分类的计收合计。
            }
        }

        Money view() { return new Money(network, digital, intelligent, total); }
    }

    private static final class Totals {
        private final Map<Integer, MoneyTotals> months = new HashMap<>();
        private final MoneyTotals total = new MoneyTotals();
        private final Set<String> names = new HashSet<>();
        private long signedCount;
        private BigDecimal signedAmount = ZERO;
        private BigDecimal recognizedAmount = ZERO;
        private boolean hasRevenue;

        void add(AnnualRecognition row) {
            hasRevenue = true;
            months.computeIfAbsent(row.month(), ignored -> new MoneyTotals()).add(row);
            total.add(row);
        }

        void sign(BigDecimal amount) {
            signedCount++;
            signedAmount = signedAmount.add(amount);
        }

        void recognize(AnnualRecognition row) {
            var name = Objects.toString(row.reportedName(), "").strip();
            if (!name.isEmpty()) names.add(name);
            recognizedAmount = recognizedAmount.add(row.amount());
        }

        List<Month> months(int current) {
            return IntStream.rangeClosed(1, 12).mapToObj(month -> {
                var values = months.get(month);
                return new Month(month, month <= current || values != null,
                        values == null ? new Money(ZERO, ZERO, ZERO, ZERO) : values.view());
            }).toList();
        }

        Money total() { return total.view(); }

        Gewu gewu() {
            long countGap = signedCount - names.size();
            var amountGap = signedAmount.subtract(recognizedAmount);
            return new Gewu(signedCount, names.size(), countGap, signedAmount, recognizedAmount, amountGap,
                    rate(BigDecimal.valueOf(countGap), BigDecimal.valueOf(signedCount)), rate(amountGap, signedAmount));
        }
    }
}
