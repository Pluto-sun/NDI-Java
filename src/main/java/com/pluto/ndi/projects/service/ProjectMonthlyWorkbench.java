package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** 固定月份的项目经营指标及历史分类。 */
public record ProjectMonthlyWorkbench(
        YearMonth month, boolean includeTerminated, long projectCount, BigDecimal totalExpectedAmount,
        List<StageTotal> stages, List<CityTotal> cities, List<IndustryTotal> industries
) {
    public ProjectMonthlyWorkbench {
        stages = List.copyOf(stages);
        cities = List.copyOf(cities);
        industries = List.copyOf(industries);
    }

    public record StageTotal(String stageCode, long projectCount, BigDecimal expectedAmount) { }
    public record CityTotal(UUID cityId, String cityName, long projectCount, BigDecimal expectedAmount) { }
    public record IndustryTotal(String industryCode, String industryName, long projectCount,
                                BigDecimal expectedAmount) { }
}
