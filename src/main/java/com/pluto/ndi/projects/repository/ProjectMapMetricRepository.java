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

    private static final String CURRENT_FROM = """
            FROM project_project project
            LEFT JOIN (
                SELECT project_id,
                       SUM(CASE WHEN category_code = 'NETWORK' THEN expected_amount ELSE 0 END) network_amount,
                       SUM(CASE WHEN category_code = 'GEWU' THEN expected_amount ELSE 0 END) gewu_amount,
                       SUM(CASE WHEN category_code = 'BIG_DATA' THEN expected_amount ELSE 0 END) big_data_amount,
                       SUM(CASE WHEN category_code = 'WANGWU' THEN expected_amount ELSE 0 END) wangwu_amount
                FROM project_category_amount GROUP BY project_id
            ) category ON category.project_id = project.id
            WHERE 1 = 1
            """;
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
        var historical = month != null;
        var regionColumn = provinceId == null ? "province_id" : "city_id";
        var stageColumn = historical ? "stage_code" : "current_stage_code";
        var categoryPrefix = historical ? "project" : "category";
        var sql = "SELECT BIN_TO_UUID(project." + regionColumn + ") AS region_id, "
                + "COUNT(*) AS project_count, COALESCE(SUM(project.total_amount), 0) AS expected_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".network_amount), 0) AS network_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".gewu_amount), 0) AS gewu_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".big_data_amount), 0) AS big_data_amount, "
                + "COALESCE(SUM(" + categoryPrefix + ".wangwu_amount), 0) AS wangwu_amount "
                + (historical ? MONTHLY_FROM : CURRENT_FROM)
                + (provinceId == null ? "" : " AND project.province_id = UUID_TO_BIN(:provinceId)")
                + (includeTerminated ? "" : " AND project." + stageColumn + " <> 'TERMINATED'")
                + (stageCode == null ? "" : " AND project." + stageColumn + " = :stageCode")
                + " GROUP BY project." + regionColumn;
        var parameters = new MapSqlParameterSource();
        if (historical) parameters.addValue("month", Date.valueOf(month.atDay(1)));
        if (provinceId != null) parameters.addValue("provinceId", provinceId.toString());
        if (stageCode != null) parameters.addValue("stageCode", stageCode);
        return jdbc.query(sql, parameters, (rs, rowNum) -> new ProjectMapMetric(
                UUID.fromString(rs.getString("region_id")), rs.getLong("project_count"),
                rs.getBigDecimal("expected_amount"), new ProjectCategoryTotals(
                        rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                        rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount"))));
    }
}
