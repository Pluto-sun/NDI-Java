package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 当前生效主档的行业与地市分组，不代表历史月末快照。 */
public record ProjectWorkbenchBreakdown(
        boolean includeTerminated,
        List<CityTotal> cities,
        List<IndustryTotal> industries
) {
    public ProjectWorkbenchBreakdown {
        cities = List.copyOf(cities);
        industries = List.copyOf(industries);
    }

    public record CityTotal(UUID cityId, String cityName, long projectCount, BigDecimal expectedAmount) { }
    public record IndustryTotal(String industryCode, String industryName,
                                long projectCount, BigDecimal expectedAmount) { }
}
