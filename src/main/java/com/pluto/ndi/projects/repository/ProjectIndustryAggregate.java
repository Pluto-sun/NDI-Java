package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;

/** 当前项目按行业汇总的数据库投影。 */
public interface ProjectIndustryAggregate {
    String getIndustryCode();
    Long getProjectCount();
    BigDecimal getExpectedAmount();
}
