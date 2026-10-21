package com.pluto.ndi.projects.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/** 一次已生成月末快照的可追溯修订。 */
public record ProjectMonthlyRevision(
        YearMonth month, UUID projectId, int revisionNo, String sourceKind, UUID sourceId,
        String reason, String beforeStageCode, String afterStageCode,
        String beforeIndustryCode, String afterIndustryCode,
        BigDecimal beforeTotalAmount, BigDecimal afterTotalAmount, Instant revisedAt,
        ProjectCategoryTotals beforeCategories, ProjectCategoryTotals afterCategories
) { }
