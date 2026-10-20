package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 项目主档详情，进展与里程碑通过各自分页接口读取。 */
public record ProjectDetailSummary(
        UUID id,
        String projectCode,
        UUID provinceId,
        UUID cityId,
        String projectName,
        String customerName,
        String industryCode,
        String customerNeed,
        BigDecimal totalAmount,
        String contractNo,
        BigDecimal signedAmount,
        String currentStageCode,
        UUID provinceLeaderUserId,
        String cityAccountManagerName,
        String signingSubjectCode,
        long versionNo,
        List<ProjectSummary.CategoryAmount> categoryAmounts,
        Instant createdAt,
        Instant updatedAt
) {
    public ProjectDetailSummary {
        categoryAmounts = List.copyOf(categoryAmounts);
    }
}
