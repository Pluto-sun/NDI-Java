package com.pluto.ndi.projects.mapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.pluto.ndi.projects.domain.ProjectOperationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 组合已持久化的事实，避免为展示审计再维护一套易失同步的业务日志。 */
@Repository
public class ProjectOperationAuditMapper {
    private static final String FACTS = """
        SELECT CONCAT('EVENT:',BIN_TO_UUID(e.id)) id,e.event_type,
               CASE e.event_type WHEN 'PROJECT_CREATED' THEN '创建项目'
                 WHEN 'PROJECT_PROFILE_UPDATED' THEN '更新项目资料与责任信息'
                 WHEN 'STAGE_ADVANCEMENT_APPROVED' THEN '阶段推进审批通过'
                 WHEN 'MAJOR_CHANGE_APPROVED' THEN '重大变更审批通过'
                 ELSE '项目资料变更' END summary,e.actor_id,e.occurred_at
        FROM project_change_event e WHERE e.project_id=UUID_TO_BIN(:project)
        UNION ALL
        SELECT CONCAT('PROGRESS:',BIN_TO_UUID(p.id)),
               IF(p.source_type='CORRECTED','PROGRESS_CORRECTED','PROGRESS_APPENDED'),
               IF(p.source_type='CORRECTED','更正项目进展','追加项目进展'),p.actor_id,p.occurred_at
        FROM project_progress p WHERE p.project_id=UUID_TO_BIN(:project) AND p.source_type IN ('APPENDED','CORRECTED')
        UNION ALL
        SELECT CONCAT('ACTION:',BIN_TO_UUID(action.id)),CONCAT('APPROVAL_',action.action_code),
               CASE action.action_code WHEN 'SUBMITTED' THEN
                 IF(a.request_type='STAGE_ADVANCEMENT','提交阶段推进申请','提交重大变更申请')
                 WHEN 'REJECTED' THEN '驳回审批申请' WHEN 'WITHDRAWN' THEN '撤销审批申请'
                 ELSE '审批因项目版本冲突未生效' END,
               action.actor_id,action.action_at
        FROM project_approval_action action JOIN project_approval_request a ON a.id=action.request_id
        WHERE a.project_id=UUID_TO_BIN(:project) AND action.action_code IN ('SUBMITTED','REJECTED','WITHDRAWN','CONFLICT')
        """;
    private final NamedParameterJdbcTemplate jdbc;

    public ProjectOperationAuditMapper(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Page<ProjectOperationAudit> find(UUID projectId, Pageable page) {
        var parameters = new MapSqlParameterSource("project", projectId.toString())
                .addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + FACTS + ") facts", parameters, Long.class);
        var rows = jdbc.query("SELECT facts.*,COALESCE(u.display_name,'系统') operator_name FROM (" + FACTS
                + ") facts LEFT JOIN identity_user_account u ON u.id=facts.actor_id"
                + " ORDER BY facts.occurred_at DESC,facts.id DESC LIMIT :limit OFFSET :offset", parameters,
                (row, index) -> new ProjectOperationAudit(row.getString("id"), row.getString("event_type"),
                        row.getString("summary"), row.getString("operator_name"),
                        row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
        return new PageImpl<>(rows, page, total == null ? 0 : total);
    }
}
