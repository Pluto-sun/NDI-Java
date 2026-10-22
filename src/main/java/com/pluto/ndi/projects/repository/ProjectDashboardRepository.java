package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectDashboardFilter;
import com.pluto.ndi.projects.domain.ProjectAttributionFilter;
import com.pluto.ndi.projects.mapper.ProjectAttributionSql;
import com.pluto.ndi.projects.mapper.ProjectCurrentMetricSql;
import com.pluto.ndi.projects.mapper.ProjectScopeSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 数据库按月、阶段、省份聚合，不把完整项目清单加载到应用内存。 */
@Repository
public class ProjectDashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ProjectDashboardRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Bucket> summarize(ProjectDataScope scope, ProjectDashboardFilter filter) {
        var parameters = new MapSqlParameterSource();
        var month = ProjectCurrentMetricSql.ATTRIBUTION_MONTH;
        var restriction = ProjectScopeSql.named(scope, "project.province_id", "project.city_id", parameters)
                + ProjectAttributionSql.named(new ProjectAttributionFilter(filter.from(), filter.to(),
                        filter.includeTerminated(), filter.stage() == null ? List.of() : List.of(filter.stage()),
                        List.of(), List.of()), parameters);
        restriction += switch (filter.category()) {
            case "NETWORK" -> " AND category.network_present = 1";
            case "DIGITAL" -> " AND (category.gewu_present = 1 OR category.big_data_present = 1)";
            case "INTELLIGENT" -> " AND category.wangwu_present = 1";
            default -> "";
        };
        restriction += switch (filter.product()) {
            case "NETWORK" -> " AND category.network_present = 1";
            case "GEWU" -> " AND category.gewu_present = 1";
            case "BIG_DATA" -> " AND category.big_data_present = 1";
            case "WANGWU" -> " AND category.wangwu_present = 1";
            default -> "";
        };
        var sql = "SELECT " + month + " metric_month, project.current_stage_code stage, "
                + "BIN_TO_UUID(project.province_id) province, COUNT(*) project_count, "
                + "SUM(project.total_amount) total_amount, "
                + "COALESCE(SUM(category.network_present),0) network_count, "
                + "COALESCE(SUM(category.gewu_present),0) gewu_count, "
                + "COALESCE(SUM(category.big_data_present),0) big_data_count, "
                + "COALESCE(SUM(category.wangwu_present),0) wangwu_count, "
                + "COALESCE(SUM(category.gewu_present = 1 OR category.big_data_present = 1),0) digital_count, "
                + "COALESCE(SUM(category.network_amount),0) network_amount, "
                + "COALESCE(SUM(category.gewu_amount),0) gewu_amount, "
                + "COALESCE(SUM(category.big_data_amount),0) big_data_amount, "
                + "COALESCE(SUM(category.wangwu_amount),0) wangwu_amount "
                + ProjectCurrentMetricSql.FROM + restriction
                + " GROUP BY " + month + ", project.current_stage_code, project.province_id";
        return jdbc.query(sql, parameters, (rs, index) -> new Bucket(
                YearMonth.from(rs.getDate("metric_month").toLocalDate()), rs.getString("stage"),
                UUID.fromString(rs.getString("province")), rs.getLong("project_count"), rs.getBigDecimal("total_amount"),
                rs.getLong("network_count"), rs.getLong("digital_count"), rs.getLong("gewu_count"),
                rs.getLong("big_data_count"), rs.getLong("wangwu_count"),
                rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount")));
    }

    public record Bucket(YearMonth month, String stage, UUID provinceId, long count, BigDecimal amount,
            long networkCount, long digitalCount, long gewuCount, long bigDataCount, long wangwuCount,
            BigDecimal networkAmount, BigDecimal gewuAmount, BigDecimal bigDataAmount, BigDecimal wangwuAmount) { }
}
