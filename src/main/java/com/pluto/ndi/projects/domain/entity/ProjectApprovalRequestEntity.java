package com.pluto.ndi.projects.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectChangeField;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_approval_request")
public class ProjectApprovalRequestEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private MajorChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiator_level", nullable = false)
    private ProjectInitiatorLevel initiatorLevel;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "base_version", nullable = false)
    private long baseVersion;

    @Column(name = "from_stage_code", nullable = false)
    private String fromStageCode;

    @Column(name = "original_project_name", nullable = false)
    private String originalProjectName;

    @Column(name = "original_total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal originalTotalAmount;

    @Column(name = "original_contract_no")
    private String originalContractNo;

    @Column(name = "original_signed_amount", precision = 18, scale = 2)
    private BigDecimal originalSignedAmount;

    @Column(name = "target_stage_code")
    private String targetStageCode;

    @Column(name = "proposed_project_name")
    private String proposedProjectName;

    @Column(name = "proposed_total_amount", precision = 18, scale = 2)
    private BigDecimal proposedTotalAmount;

    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;

    @Column(name = "stage_occurred_at")
    private Instant stageOccurredAt;

    @Column(name = "proposed_contract_no")
    private String proposedContractNo;

    @Column(name = "proposed_signed_amount", precision = 18, scale = 2)
    private BigDecimal proposedSignedAmount;

    @Column(name = "proposed_progress_content")
    private String proposedProgressContent;

    @Column(name = "proposed_next_action")
    private String proposedNextAction;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "review_opinion")
    private String reviewOpinion;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ElementCollection
    @CollectionTable(name = "project_approval_field_change", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "sort_order")
    @BatchSize(size = 50)
    private List<ProjectApprovalFieldChange> fieldChanges = new ArrayList<>();

    protected ProjectApprovalRequestEntity() {
    }

    public static ProjectApprovalRequestEntity majorChange(
            MajorChangeType changeType, ProjectInitiatorLevel initiatorLevel,
            UUID initiatorId, UUID approverId, ProjectEntity project,
            String targetStageCode, String proposedProjectName, BigDecimal proposedTotalAmount,
            String changeReason, Instant submittedAt
    ) {
        var entity = new ProjectApprovalRequestEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = project.id();
        entity.requestType = "MAJOR_CHANGE";
        entity.changeType = changeType;
        entity.status = ApprovalStatus.PENDING;
        entity.initiatorLevel = initiatorLevel;
        entity.initiatorId = initiatorId;
        entity.approverId = approverId;
        entity.baseVersion = project.versionNo();
        entity.fromStageCode = project.currentStageCode();
        entity.originalProjectName = project.projectName();
        entity.originalTotalAmount = project.totalAmount();
        entity.originalContractNo = project.contractNo();
        entity.originalSignedAmount = project.signedAmount();
        entity.targetStageCode = targetStageCode;
        entity.proposedProjectName = proposedProjectName;
        entity.proposedTotalAmount = proposedTotalAmount;
        entity.changeReason = changeReason;
        entity.submittedAt = submittedAt;
        entity.dueAt = submittedAt.plusSeconds(48 * 60 * 60);
        return entity;
    }

    public static ProjectApprovalRequestEntity majorChangeFields(ProjectInitiatorLevel level, UUID initiatorId,
            UUID approverId, ProjectEntity project, List<ProjectApprovalFieldChange> changes, String reason, Instant at) {
        var target = proposed(changes, ProjectChangeField.STAGE);
        var name = proposed(changes, ProjectChangeField.PROJECT_NAME);
        var amount = proposed(changes, ProjectChangeField.TOTAL_AMOUNT);
        var entity = majorChange(MajorChangeType.FIELD_CHANGES, level, initiatorId, approverId, project,
                target, name, amount == null ? null : new BigDecimal(amount), reason, at);
        entity.fieldChanges.addAll(changes);
        return entity;
    }

    private static String proposed(List<ProjectApprovalFieldChange> changes, ProjectChangeField field) {
        return changes.stream().filter(item -> item.fieldCode() == field).map(ProjectApprovalFieldChange::proposedValue)
                .findFirst().orElse(null);
    }

    public static ProjectApprovalRequestEntity stageAdvancement(
            ProjectEntity project, ProjectInitiatorLevel initiatorLevel,
            UUID initiatorId, UUID approverId, String targetStageCode,
            UUID evidenceDocumentId, Instant stageOccurredAt, String proposedContractNo,
            BigDecimal proposedSignedAmount, String progressContent, String nextAction, Instant submittedAt
    ) {
        var entity = new ProjectApprovalRequestEntity();
        entity.id = UUID.randomUUID();
        entity.projectId = project.id();
        entity.requestType = "STAGE_ADVANCEMENT";
        entity.status = ApprovalStatus.PENDING;
        entity.initiatorLevel = initiatorLevel;
        entity.initiatorId = initiatorId;
        entity.approverId = approverId;
        entity.baseVersion = project.versionNo();
        entity.fromStageCode = project.currentStageCode();
        entity.originalProjectName = project.projectName();
        entity.originalTotalAmount = project.totalAmount();
        entity.originalContractNo = project.contractNo();
        entity.originalSignedAmount = project.signedAmount();
        entity.targetStageCode = targetStageCode;
        entity.evidenceDocumentId = evidenceDocumentId;
        entity.stageOccurredAt = stageOccurredAt;
        entity.proposedContractNo = proposedContractNo;
        entity.proposedSignedAmount = proposedSignedAmount;
        entity.proposedProgressContent = progressContent;
        entity.proposedNextAction = nextAction;
        entity.submittedAt = submittedAt;
        entity.dueAt = submittedAt.plusSeconds(48 * 60 * 60);
        return entity;
    }

    public void finish(ApprovalStatus newStatus, String opinion, Instant at) {
        if (status != ApprovalStatus.PENDING || newStatus == ApprovalStatus.PENDING) {
            throw new IllegalStateException("审批申请状态不可重复变更");
        }
        status = newStatus;
        reviewOpinion = opinion;
        decidedAt = at;
    }

    public UUID id() { return id; }
    public UUID projectId() { return projectId; }
    public String requestType() { return requestType; }
    public MajorChangeType changeType() { return changeType; }
    public ApprovalStatus status() { return status; }
    public ProjectInitiatorLevel initiatorLevel() { return initiatorLevel; }
    public UUID initiatorId() { return initiatorId; }
    public UUID approverId() { return approverId; }
    public long baseVersion() { return baseVersion; }
    public String fromStageCode() { return fromStageCode; }
    public String originalProjectName() { return originalProjectName; }
    public BigDecimal originalTotalAmount() { return originalTotalAmount; }
    public String originalContractNo() { return originalContractNo; }
    public BigDecimal originalSignedAmount() { return originalSignedAmount; }
    public String targetStageCode() { return targetStageCode; }
    public String proposedProjectName() { return proposedProjectName; }
    public BigDecimal proposedTotalAmount() { return proposedTotalAmount; }
    public UUID evidenceDocumentId() { return evidenceDocumentId; }
    public Instant stageOccurredAt() { return stageOccurredAt; }
    public String proposedContractNo() { return proposedContractNo; }
    public BigDecimal proposedSignedAmount() { return proposedSignedAmount; }
    public String proposedProgressContent() { return proposedProgressContent; }
    public String proposedNextAction() { return proposedNextAction; }
    public String changeReason() { return changeReason; }
    public String reviewOpinion() { return reviewOpinion; }
    public Instant submittedAt() { return submittedAt; }
    public Instant dueAt() { return dueAt; }
    public Instant decidedAt() { return decidedAt; }
    public List<ProjectApprovalFieldChange> fieldChanges() { return List.copyOf(fieldChanges); }
}
