package com.pluto.ndi.projects.mapper;

import java.sql.Date;
import com.pluto.ndi.projects.domain.ProjectAttributionFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** 在项目授权范围之外追加报表筛选；筛选条件永远不能扩大授权。 */
public final class ProjectAttributionSql {
    private ProjectAttributionSql() { }

    public static String named(ProjectAttributionFilter filter, MapSqlParameterSource parameters) {
        parameters.addValue("fromMonth", Date.valueOf(filter.from().atDay(1)))
                .addValue("toMonth", Date.valueOf(filter.to().atDay(1)));
        var sql = new StringBuilder(" AND " + ProjectCurrentMetricSql.ATTRIBUTION_MONTH + " BETWEEN :fromMonth AND :toMonth");
        if (!filter.includeTerminated()) sql.append(" AND project.current_stage_code <> 'TERMINATED'");
        if (!filter.stages().isEmpty()) {
            sql.append(" AND project.current_stage_code IN (:attributionStages)");
            parameters.addValue("attributionStages", filter.stages().stream().map(Enum::name).toList());
        }
        if (!filter.provinceIds().isEmpty()) {
            sql.append(" AND project.province_id IN (:filterProvinces)");
            parameters.addValue("filterProvinces", ProjectScopeSql.binaryIds(filter.provinceIds()));
        }
        if (!filter.cityIds().isEmpty()) {
            sql.append(" AND project.city_id IN (:filterCities)");
            parameters.addValue("filterCities", ProjectScopeSql.binaryIds(filter.cityIds()));
        }
        return sql.toString();
    }
}
