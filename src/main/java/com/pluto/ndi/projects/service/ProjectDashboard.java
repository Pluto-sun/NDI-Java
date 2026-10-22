package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** 驾驶舱数据契约；数量与金额分别汇总，跨分类的数量允许重复。 */
public record ProjectDashboard(YearMonth from, YearMonth to, Summary current, Summary previous,
        List<Stage> stages, List<Region> provinces, List<Month> trend) {
    public record Value(long count, BigDecimal amount) { }
    public record Summary(long projectCount, BigDecimal totalAmount, long provinceCount,
            Value network, Value digital, Value intelligent, Value gewu, Value bigData) { }
    public record Stage(String stageCode, Summary summary) { }
    public record Region(UUID id, String name, Summary summary) { }
    public record Month(YearMonth month, Summary summary) { }
}
