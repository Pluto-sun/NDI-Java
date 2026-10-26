package com.pluto.ndi.projects.service;

import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalActionEntity;
import com.pluto.ndi.projects.repository.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** 身份范围的同步订阅者，不改变项目内容、审批期限和材料。 */
@Service
public class ProjectReviewerHandoverService {
    private final IdentityDirectory identities;
    private final NamedParameterJdbcTemplate jdbc;
    private final ProjectRepository projects;
    private final ProjectApprovalRequestRepository requests;
    private final ProjectApprovalActionRepository actions;
    private final ProjectApprovalNotificationService notices;
    private final AuditWriter audit;
    private final Clock clock;

    public ProjectReviewerHandoverService(
            IdentityDirectory identities,
            NamedParameterJdbcTemplate jdbc,
            ProjectRepository projects,
            ProjectApprovalRequestRepository requests,
            ProjectApprovalActionRepository actions,
            ProjectApprovalNotificationService notices,
            AuditWriter audit,
            Clock clock) {
        this.identities = identities;
        this.jdbc = jdbc;
        this.projects = projects;
        this.requests = requests;
        this.actions = actions;
        this.notices = notices;
        this.audit = audit;
        this.clock = clock;
    }

    public record Pending(UUID requestId, UUID projectId, UUID provinceId, UUID approverId) {}

    private List<Pending> pending(Set<UUID> provinces) {
        if (provinces != null && provinces.isEmpty()) return List.of();
        String restriction = provinces == null ? "" : " AND p.province_id IN (:provinces) ";
        var parameters = new HashMap<String, Object>();
        if (provinces != null)
            parameters.put(
                    "provinces",
                    provinces.stream()
                            .map(
                                    id ->
                                            java.nio.ByteBuffer.allocate(16)
                                                    .putLong(id.getMostSignificantBits())
                                                    .putLong(id.getLeastSignificantBits())
                                                    .array())
                            .toList());
        return jdbc.query(
                """
SELECT BIN_TO_UUID(r.id) request_id,BIN_TO_UUID(r.project_id) project_id,
       BIN_TO_UUID(p.province_id) province_id,BIN_TO_UUID(r.approver_id) approver_id
FROM project_approval_request r JOIN project_project p ON p.id=r.project_id
WHERE r.status='PENDING' AND (r.request_type='STAGE_ADVANCEMENT' OR r.initiator_level='PROVINCE')
"""
                        + restriction
                        + " ORDER BY r.project_id,r.id",
                parameters,
                (rs, row) ->
                        new Pending(
                                UUID.fromString(rs.getString("request_id")),
                                UUID.fromString(rs.getString("project_id")),
                                UUID.fromString(rs.getString("province_id")),
                                UUID.fromString(rs.getString("approver_id"))));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(ReviewerScopeChanged event) {
        var at = Instant.now(clock);
        for (var item : pending(event.provinceIds())) {
            var candidates = identities.findEligibleStageApprovers(item.provinceId());
            if (candidates.isEmpty()) continue; // 账号停用不被业务待办阻断；待交接清单保留未分配待办。
            var target = candidates.getFirst().id();
            projects.findLockedById(item.projectId()).orElseThrow();
            var request = requests.findLockedById(item.requestId()).orElseThrow();
            if (request.status() != ApprovalStatus.PENDING || request.approverId().equals(target))
                continue;
            if (request.initiatorId().equals(target))
                throw new BusinessException(ProjectErrorCode.SELF_APPROVAL_FORBIDDEN);
            var previous = request.approverId();
            request.reassign(target);
            actions.save(
                    ProjectApprovalActionEntity.create(
                            request.id(), "REASSIGNED", event.actorId(), at, "管理员交接接口人负责范围"));
            notices.transferred(request, previous, at);
            audit.record(
                    event.actorId(),
                    "PROJECT_APPROVAL",
                    request.id(),
                    "PROJECT_APPROVAL_REASSIGNED",
                    Map.of(
                            "previousApproverId",
                            previous,
                            "approverId",
                            target,
                            "provinceId",
                            item.provinceId()));
        }
    }

    @Transactional(readOnly = true)
    public List<Pending> unassigned(UUID actorId) {
        if (!identities.isSystemAdministrator(actorId))
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        return pending(null).stream()
                .filter(
                        item ->
                                !identities.isEligibleStageApprover(
                                        item.approverId(), item.provinceId()))
                .toList();
    }
}
