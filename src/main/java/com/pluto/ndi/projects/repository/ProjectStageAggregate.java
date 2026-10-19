package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;

/** 数据库分组后的当前项目阶段汇总。 */
public interface ProjectStageAggregate {

    String getStageCode();

    Long getProjectCount();

    BigDecimal getExpectedAmount();
}
