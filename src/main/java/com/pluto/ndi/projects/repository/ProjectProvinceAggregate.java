package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** 全国地图按省份汇总的当前项目指标。 */
public interface ProjectProvinceAggregate {
    UUID getProvinceId();
    long getProjectCount();
    BigDecimal getExpectedAmount();
}
