package com.pluto.ndi.shared.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 各台账在自己的读取范围内聚合。金额统一为万元，项目数在整个区间去重。 */
public record SchedulingOverviewSource(long projectCount, List<Group> groups) {
    public record Group(LocalDate month, String provinceId, String provinceName, Map<String, BigDecimal> amounts) {}
}
