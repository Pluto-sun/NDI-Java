package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        Instant createdAt,
        Instant opportunityDate,
        String importanceCode,
        UUID provinceId,
        UUID cityId,
        UUID provinceLeaderUserId,
        String provinceLeaderName,
        String signingSubjectCode,
        LocalDate attributionMonth
) {

    public ProjectSummary {
        categoryAmounts = List.copyOf(categoryAmounts);
    }

    public ProjectSummary(UUID id, String projectCode, String projectName, String customerName, String industryCode,
            BigDecimal totalAmount, String currentStageCode, List<CategoryAmount> categoryAmounts, Instant createdAt,
            Instant opportunityDate, String importanceCode) {
        this(id, projectCode, projectName, customerName, industryCode, totalAmount, currentStageCode,
                categoryAmounts, createdAt, opportunityDate, importanceCode, null, null, null, null, "PROVINCE_SIGN", null);
    }

    public ProjectSummary(UUID id, String projectCode, String projectName, String customerName, String industryCode,
            BigDecimal totalAmount, String currentStageCode, List<CategoryAmount> categoryAmounts, Instant createdAt,
            Instant opportunityDate) {
        this(id, projectCode, projectName, customerName, industryCode, totalAmount, currentStageCode,
                categoryAmounts, createdAt, opportunityDate, "NORMAL");
    }

    public ProjectSummary(UUID id, String projectCode, String projectName, String customerName, String industryCode,
            BigDecimal totalAmount, String currentStageCode, List<CategoryAmount> categoryAmounts, Instant createdAt) {
        this(id, projectCode, projectName, customerName, industryCode, totalAmount, currentStageCode, categoryAmounts, createdAt, null);
    }

    /** 用于展示的三化类别金额。 */
    public record CategoryAmount(String categoryCode, BigDecimal expectedAmount) {
    }
}
