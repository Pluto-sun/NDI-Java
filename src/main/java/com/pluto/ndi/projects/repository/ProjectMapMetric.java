package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectCategoryTotals;

/** 地图按行政区域聚合后的经营指标。 */
public record ProjectMapMetric(UUID regionId, long projectCount, BigDecimal expectedAmount,
                               ProjectCategoryTotals categoryTotals) { }
