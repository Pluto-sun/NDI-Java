package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.List;

/** 当前项目经营概览，不代表历史月末快照。 */
public record ProjectWorkbenchOverview(
        boolean includeTerminated,
        long projectCount,
        BigDecimal totalExpectedAmount,
        List<StageTotal> stages
) {
    public ProjectWorkbenchOverview {
        stages = List.copyOf(stages);
    }

    /** 每个生命周期阶段的当前项目数与预期金额。 */
    public record StageTotal(String stageCode, long projectCount, BigDecimal expectedAmount) {
    }
}
