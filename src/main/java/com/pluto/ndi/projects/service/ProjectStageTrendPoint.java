package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;

import com.pluto.ndi.projects.domain.ProjectCategoryTotals;

/** 单个月末、单个项目阶段的经营指标；月份之间不累计。 */
public record ProjectStageTrendPoint(YearMonth month, String stageCode, long projectCount,
                                     BigDecimal totalExpectedAmount, ProjectCategoryTotals categoryTotals) { }
