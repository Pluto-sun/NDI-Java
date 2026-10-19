package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;

/** 指定审批人的待办摘要，不含申请中的敏感字段全文。 */
public record ApprovalTaskSummary(
        UUID requestId,
        UUID projectId,
        String projectName,
        String requestType,
        String fromStageCode,
        String targetStageCode,
        UUID initiatorId,
        Instant submittedAt,
        Instant dueAt,
        boolean overdue
) {
    public static ApprovalTaskSummary from(ProjectApprovalRequestEntity request, Instant now) {
        return new ApprovalTaskSummary(
                request.id(), request.projectId(), request.originalProjectName(), request.requestType(),
                request.fromStageCode(), request.targetStageCode(), request.initiatorId(),
                request.submittedAt(), request.dueAt(), !request.dueAt().isAfter(now)
        );
    }
}
