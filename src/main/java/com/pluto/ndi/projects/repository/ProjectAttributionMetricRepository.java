package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectCategoryTotals;
import com.pluto.ndi.projects.mapper.ProjectCurrentMetricSql;
import com.pluto.ndi.projects.mapper.ProjectScopeSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 每个项目仅按当前唯一所属月份计数；未读取月末快照表。 */
@Repository
public class ProjectAttributionMetricRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ProjectAttributionMetricRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Metric> summarize(ProjectDataScope scope, YearMonth from, YearMonth to,
            boolean includeTerminated, List<String> stages, List<UUID> provinces, List<UUID> cities) {
        var parameters = new MapSqlParameterSource().addValue("fromMonth", Date.valueOf(from.atDay(1)))
                .addValue("toMonth", Date.valueOf(to.atDay(1)));
        var scopeSql = ProjectScopeSql.named(scope, "project.province_id", "project.city_id", parameters);
        var month = ProjectCurrentMetricSql.ATTRIBUTION_MONTH;
        var sql = "SELECT " + month + " attribution_month, project.current_stage_code stage_code, "
                + "BIN_TO_UUID(project.province_id) province_id, BIN_TO_UUID(project.city_id) city_id, "
                + "COUNT(*) project_count, SUM(project.total_amount) total_amount, "
                + "COALESCE(SUM(category.network_amount),0) network_amount, "
                + "COALESCE(SUM(category.gewu_amount),0) gewu_amount, "
                + "COALESCE(SUM(category.big_data_amount),0) big_data_amount, "
                + "COALESCE(SUM(category.wangwu_amount),0) wangwu_amount "
                + ProjectCurrentMetricSql.FROM + scopeSql
                + " AND " + month + " BETWEEN :fromMonth AND :toMonth"
                + (includeTerminated ? "" : " AND project.current_stage_code <> 'TERMINATED'")
                + (stages.isEmpty() ? "" : " AND project.current_stage_code IN (:stages)")
                + (provinces.isEmpty() ? "" : " AND project.province_id IN (:provinces)")
                + (cities.isEmpty() ? "" : " AND project.city_id IN (:cities)")
                + " GROUP BY " + month + ", project.current_stage_code, project.province_id, project.city_id";
        if (!stages.isEmpty()) parameters.addValue("stages", stages);
        if (!provinces.isEmpty()) parameters.addValue("provinces", provinces.stream().map(ProjectAttributionMetricRepository::bytes).toList());
        if (!cities.isEmpty()) parameters.addValue("cities", cities.stream().map(ProjectAttributionMetricRepository::bytes).toList());
        return jdbc.query(sql, parameters, (rs, index) -> new Metric(
                YearMonth.from(rs.getDate("attribution_month").toLocalDate()), rs.getString("stage_code"),
                UUID.fromString(rs.getString("province_id")), UUID.fromString(rs.getString("city_id")),
                rs.getLong("project_count"), rs.getBigDecimal("total_amount"),
                new ProjectCategoryTotals(rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                        rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount"))));
    }

    private static byte[] bytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    public record Metric(YearMonth month, String stageCode, UUID provinceId, UUID cityId,
                         long projectCount, BigDecimal totalAmount, ProjectCategoryTotals categoryTotals) { }
}
