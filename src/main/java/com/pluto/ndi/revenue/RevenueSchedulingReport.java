package com.pluto.ndi.revenue;

import com.pluto.ndi.revenue.mapper.RevenueMapper;
import com.pluto.ndi.revenue.service.RevenueAccessService;
import com.pluto.ndi.shared.reporting.DashboardSummary.MonthRange;
import com.pluto.ndi.shared.reporting.SchedulingOverviewSource;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 向排产驾驶舱提供按计收台账权限聚合的生效认定数据。 */
@Service
public class RevenueSchedulingReport {
    private final RevenueAccessService access;
    private final RevenueMapper revenue;

    public RevenueSchedulingReport(RevenueAccessService access, RevenueMapper revenue) {
        this.access = access;
        this.revenue = revenue;
    }

    public Result load(UUID actor, MonthRange range) {
        var scope = access.require(actor);
        return new Result(scope.national(), revenue.schedulingOverview(scope, range));
    }

    public record Result(boolean national, SchedulingOverviewSource source) {}
}
