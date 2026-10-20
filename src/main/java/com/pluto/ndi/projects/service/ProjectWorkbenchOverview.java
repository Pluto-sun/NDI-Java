package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.List;

/** 当前项目经营概览，不代表历史月末快照。 */
public record ProjectWorkbenchOverview(
        boolean includeTerminated,
        long projectCount,
        BigDecimal totalExpectedAmount,
        List<StageTotal> stages,
        ApprovalEntry approvals
) {
    public ProjectWorkbenchOverview {
        stages = List.copyOf(stages);
    }

    /** 每个生命周期阶段的当前项目数与预期金额。 */
    public record StageTotal(String stageCode, long projectCount, BigDecimal expectedAmount) {
    }

    /** 工作台仅提供待办数量，明细由指定审批人待办接口读取。 */
    public record ApprovalEntry(long pendingCount, long overdueCount, String inboxApiPath) { }
}
