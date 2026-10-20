package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;
import java.util.UUID;

/** 当前项目按地市汇总的数据库投影。 */
public interface ProjectCityAggregate {
    UUID getCityId();
    Long getProjectCount();
    BigDecimal getExpectedAmount();
}
