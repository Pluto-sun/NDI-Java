package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;

/** 审批申请的只读展示数据。 */
public record ApprovalSummary(
        UUID id,
        UUID projectId,
        String requestType,
        MajorChangeType changeType,
        ApprovalStatus status,
        UUID initiatorId,
        UUID approverId,
        long baseVersion,
        String fromStageCode,
        String originalProjectName,
        BigDecimal originalTotalAmount,
        String originalContractNo,
        BigDecimal originalSignedAmount,
        String targetStageCode,
        String proposedProjectName,
        BigDecimal proposedTotalAmount,
        UUID evidenceDocumentId,
        Instant stageOccurredAt,
        String proposedContractNo,
        BigDecimal proposedSignedAmount,
        String proposedProgressContent,
        String proposedNextAction,
        String changeReason,
        String reviewOpinion,
        Instant submittedAt,
        Instant dueAt,
        Instant decidedAt,
        List<ApprovalFieldChangeSummary> fieldChanges
) {
    public static ApprovalSummary from(ProjectApprovalRequestEntity request) {
        return new ApprovalSummary(
                request.id(), request.projectId(), request.requestType(), request.changeType(), request.status(),
                request.initiatorId(), request.approverId(), request.baseVersion(),
                request.fromStageCode(), request.originalProjectName(), request.originalTotalAmount(),
                request.originalContractNo(), request.originalSignedAmount(),
                request.targetStageCode(), request.proposedProjectName(),
                request.proposedTotalAmount(), request.evidenceDocumentId(), request.stageOccurredAt(),
                request.proposedContractNo(), request.proposedSignedAmount(),
                request.proposedProgressContent(), request.proposedNextAction(),
                request.changeReason(), request.reviewOpinion(),
                request.submittedAt(), request.dueAt(), request.decidedAt(),
                request.fieldChanges().stream().map(item -> new ApprovalFieldChangeSummary(
                        item.fieldCode(), item.originalValue(), item.proposedValue())).toList()
        );
    }
}
