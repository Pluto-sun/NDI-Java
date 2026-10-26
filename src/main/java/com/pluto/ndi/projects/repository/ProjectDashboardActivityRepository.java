package com.pluto.ndi.projects.repository;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectDashboardFilter;
import com.pluto.ndi.projects.domain.ProjectDashboardActivity;
import com.pluto.ndi.projects.mapper.ProjectScopeSql;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 仅展示生效变更；审批前后值取事件载荷，无法恢复的旧值保持为空。 */
@Repository
public class ProjectDashboardActivityRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ProjectDashboardActivityRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ProjectDashboardActivity> recent(ProjectDataScope scope, ProjectDashboardFilter filter) {
        var zone = ZoneId.of("Asia/Shanghai");
        var parameters = new MapSqlParameterSource()
                .addValue("since", java.sql.Timestamp.from(filter.from().atDay(1).atStartOfDay(zone).toInstant()))
                .addValue("until", java.sql.Timestamp.from(filter.to().plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()));
        var scopeSql = ProjectScopeSql.named(scope, "project.province_id", "project.city_id", parameters);
        var stageSql = filter.stage() == null ? "" : " AND project.current_stage_code = :stage";
        if (filter.stage() != null) parameters.addValue("stage", filter.stage().name());
        var sql = """
                SELECT BIN_TO_UUID(event.id) id, BIN_TO_UUID(project.id) project_id,
                       project.project_name, province.province_name, industry.industry_name,
                       leader.display_name leader_name, event.event_type, event.occurred_at,
                       category.products,
                       JSON_UNQUOTE(JSON_EXTRACT(event.payload, '$.before.stageCode')) before_stage,
                       COALESCE(JSON_UNQUOTE(JSON_EXTRACT(event.payload, '$.after.stageCode')),
                           JSON_UNQUOTE(JSON_EXTRACT(event.payload, '$.currentStageCode')),
                           project.current_stage_code) after_stage,
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload, '$.before.totalAmount')) AS DECIMAL(18,2)) before_amount,
                       CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(event.payload, '$.after.totalAmount')),
                           JSON_UNQUOTE(JSON_EXTRACT(event.payload, '$.totalAmount'))) AS DECIMAL(18,2)) after_amount
                FROM project_change_event event
                JOIN project_project project ON project.id = event.project_id
                JOIN organization_province province ON province.id = project.province_id
                JOIN project_industry_reference industry ON industry.industry_code = project.industry_code
                LEFT JOIN identity_user_account leader ON leader.id = project.province_leader_user_id
                LEFT JOIN (SELECT project_id, GROUP_CONCAT(category_code ORDER BY category_code) products
                           FROM project_category_amount GROUP BY project_id) category ON category.project_id = project.id
                WHERE event.occurred_at >= :since AND event.occurred_at < :until
                """ + scopeSql + stageSql
                + (filter.includeTerminated() ? "" : " AND project.current_stage_code <> 'TERMINATED'")
                + " ORDER BY event.occurred_at DESC, event.id DESC LIMIT 100";
        return jdbc.query(sql, parameters, (rs, index) -> new ProjectDashboardActivity(rs.getString("id"),
                UUID.fromString(rs.getString("project_id")), rs.getString("project_name"),
                rs.getString("province_name"), rs.getString("industry_name"), rs.getString("products"),
                rs.getString("leader_name"), rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("before_stage"), rs.getString("after_stage"),
                rs.getBigDecimal("before_amount"), rs.getBigDecimal("after_amount")));
    }
}
