package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 项目工作台地图的授权区域与当前经营指标。 */
public record ProjectMapView(
        String level,
        String mapAdcode,
        Province province,
        boolean includeTerminated,
        String stageCode,
        List<Region> regions
) {
    public ProjectMapView {
        regions = List.copyOf(regions);
    }

    public record Province(UUID id, String name) { }

    public record Region(UUID id, String name, String adcode,
                         long projectCount, BigDecimal expectedAmount, boolean authorized) { }
}
