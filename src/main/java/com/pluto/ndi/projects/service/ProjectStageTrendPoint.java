package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;

import com.pluto.ndi.projects.domain.ProjectCategoryTotals;

/** 单月份、单阶段的经营指标；具体口径由所属月份或月末快照接口分别声明。 */
public record ProjectStageTrendPoint(YearMonth month, String stageCode, long projectCount,
                                     BigDecimal totalExpectedAmount, ProjectCategoryTotals categoryTotals) { }
