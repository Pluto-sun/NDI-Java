package com.pluto.ndi.projects.repository;

import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectCategoryTotals;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 当前主档与月末快照共用的地图聚合查询。 */
@Repository
public class ProjectMapMetricRepository {

    private static final String MONTHLY_FROM = """
            FROM project_monthly_snapshot project
            WHERE project.month_start = :month
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProjectMapMetricRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProjectMapMetric> summarize(YearMonth month, UUID provinceId,
            boolean includeTerminated, String stageCode) {
        return summarize(month, provinceId, includeTerminated, stageCode,
                new com.pluto.ndi.identity.service.ProjectDataScope(com.pluto.ndi.identity.service.ProjectDataScope.Level.NATIONAL, null));
    }

    public List<ProjectMapMetric> summarize(YearMonth month, UUID provinceId,
            boolean includeTerminated, String stageCode, com.pluto.ndi.identity.service.ProjectDataScope scope) {
        return summarizeStages(month, provinceId, includeTerminated,
                stageCode == null ? List.of() : List.of(stageCode), scope);
    }

    public List<ProjectMapMetric> summarizeStages(YearMonth month, UUID provinceId,
            boolean includeTerminated, List<String> stageCodes, com.pluto.ndi.identity.service.ProjectDataScope scope) {
        var historical = month != null;
        var regionColumn = provinceId == null ? "province_id" : "city_id";
        var stageColumn = historical ? "stage_code" : "current_stage_code";
        var categoryPrefix = historical ? "project" : "category";
        var parameters = new MapSqlParameterSource();
        var scopeFilter = com.pluto.ndi.projects.mapper.ProjectScopeSql.named(scope, "project.province_id", "project.city_id", parameters);
        var sql = "SELECT BIN_TO_UUID(project." + regionColumn + ") AS region_id, "
                + "COUNT(*) AS project_count, COALESCE(SUM(project.total_amount), 0) AS expected_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".network_amount), 0) AS network_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".gewu_amount), 0) AS gewu_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".big_data_amount), 0) AS big_data_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".wangwu_amount), 0) AS wangwu_amount "
                + (historical ? MONTHLY_FROM : com.pluto.ndi.projects.mapper.ProjectCurrentMetricSql.FROM)
                + scopeFilter
                + (provinceId == null ? "" : " AND project.province_id = UUID_TO_BIN(:provinceId)")
                + (includeTerminated ? "" : " AND project." + stageColumn + " <> 'TERMINATED'")
                + (stageCodes.isEmpty() ? "" : " AND project." + stageColumn + " IN (:stageCodes)")
                + " GROUP BY project." + regionColumn;
        if (historical) parameters.addValue("month", Date.valueOf(month.atDay(1)));
        if (provinceId != null) parameters.addValue("provinceId", provinceId.toString());
        if (!stageCodes.isEmpty()) parameters.addValue("stageCodes", stageCodes);
        return jdbc.query(sql, parameters, (rs, rowNum) -> new ProjectMapMetric(
                UUID.fromString(rs.getString("region_id")), rs.getLong("project_count"),
                rs.getBigDecimal("expected_amount"), new ProjectCategoryTotals(
                        rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                        rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount"))));
    }
}
