package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 项目创建和列表查询共用的摘要输出。 */
public record ProjectSummary(
        UUID id,
        String projectCode,
        String projectName,
        String customerName,
        String industryCode,
        BigDecimal totalAmount,
        String currentStageCode,
        List<CategoryAmount> categoryAmounts,
        Instant createdAt
) {

    public ProjectSummary {
        categoryAmounts = List.copyOf(categoryAmounts);
    }

    /** 用于展示的三化类别金额。 */
    public record CategoryAmount(String categoryCode, BigDecimal expectedAmount) {
    }
}
