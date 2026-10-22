package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectCategoryTotals;

/** 项目工作台地图的授权区域与当前经营指标。 */
public record ProjectMapView(
        String level,
        String mapAdcode,
        Province province,
        YearMonth month,
        boolean includeTerminated,
        String stageCode,
        List<Region> regions,
        List<String> stageCodes,
        YearMonth attributionFrom,
        YearMonth attributionTo
) {
    public ProjectMapView {
        regions = List.copyOf(regions);
        stageCodes = List.copyOf(stageCodes);
    }

    public ProjectMapView(String level, String mapAdcode, Province province, YearMonth month,
            boolean includeTerminated, String stageCode, List<Region> regions, List<String> stageCodes) {
        this(level, mapAdcode, province, month, includeTerminated, stageCode, regions, stageCodes, null, null);
    }

    public record Province(UUID id, String name) { }

    public record Region(UUID id, String name, String adcode,
                         long projectCount, BigDecimal expectedAmount,
                         ProjectCategoryTotals categoryTotals, boolean authorized) { }
}
