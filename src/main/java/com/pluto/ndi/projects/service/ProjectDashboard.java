package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** 驾驶舱数据契约；数量与金额分别汇总，跨分类的数量允许重复。 */
public record ProjectDashboard(YearMonth from, YearMonth to, Summary current,
        List<Stage> stages, List<Region> provinces, List<Month> trend) {
    public record Value(long count, BigDecimal amount) { }
    public record Summary(long projectCount, BigDecimal totalAmount, long provinceCount,
            Value network, Value digital, Value intelligent, Value gewu, Value bigData, Value wangwu) { }
    public record Stage(String stageCode, Summary summary) { }
    public record Region(UUID id, String name, String adcode, Summary summary, List<Region> cities) { }
    public record ProductStage(String stageCode, long count, BigDecimal amount) { }
    public record Month(YearMonth month, Summary summary, List<Stage> stages,
            String source, List<ProductStage> productStages) {
        public Month(YearMonth month, Summary summary, List<Stage> stages) {
            this(month, summary, stages, null, List.of());
        }
    }
}
