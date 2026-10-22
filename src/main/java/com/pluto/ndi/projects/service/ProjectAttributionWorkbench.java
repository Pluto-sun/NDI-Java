package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.pluto.ndi.projects.domain.ProjectCategoryTotals;

/** 项目唯一所属月份区间汇总，与月末存量快照分开返回。 */
public record ProjectAttributionWorkbench(YearMonth from, YearMonth to, boolean includeTerminated,
        long projectCount, BigDecimal totalExpectedAmount, ProjectCategoryTotals categoryTotals,
        List<ProjectStageTrendPoint> trend, List<Region> provinces, List<Region> cities) {
    public ProjectAttributionWorkbench {
        trend = List.copyOf(trend);
        provinces = List.copyOf(provinces);
        cities = List.copyOf(cities);
    }

    public record Region(UUID id, String name, long projectCount, BigDecimal expectedAmount,
                         ProjectCategoryTotals categoryTotals) { }
}
