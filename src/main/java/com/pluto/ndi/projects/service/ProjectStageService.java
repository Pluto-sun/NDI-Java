package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ProjectDocumentPurpose;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalActionEntity;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;
import com.pluto.ndi.projects.repository.ProjectApprovalActionRepository;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目阶段推进规则与审批生效动作。 */
@Service
public class ProjectStageService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final IdentityDirectory identityDirectory;
    private final DocumentDirectory documentDirectory;
    private final ProjectRepository projectRepository;
    private final ProjectApprovalRequestRepository approvalRepository;
    private final ProjectApprovalActionRepository actionRepository;
    private final ProjectStageMilestoneRepository milestoneRepository;
    private final ProjectProgressRepository progressRepository;
    private final ProjectApprovalNotificationService notificationService;
    private final Clock clock;

    public ProjectStageService(
            IdentityDirectory identityDirectory, DocumentDirectory documentDirectory,
            ProjectRepository projectRepository, ProjectApprovalRequestRepository approvalRepository,
            ProjectApprovalActionRepository actionRepository,
            ProjectStageMilestoneRepository milestoneRepository, ProjectProgressRepository progressRepository,
            ProjectApprovalNotificationService notificationService,
            Clock clock
    ) {
        this.identityDirectory = identityDirectory;
        this.documentDirectory = documentDirectory;
        this.projectRepository = projectRepository;
        this.approvalRepository = approvalRepository;
        this.actionRepository = actionRepository;
        this.milestoneRepository = milestoneRepository;
        this.progressRepository = progressRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public ApprovalSummary submit(UUID actorId, UUID projectId, StageAdvanceCommand command) {
        var project = projectRepository.findLockedById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        var actor = identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        if (command == null || command.initiatorLevel() == null || !identityDirectory.canAdvanceProjectStage(
                actor, project.provinceId(), project.cityId(), command.initiatorLevel())) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        if (command.approverId() == null || actorId.equals(command.approverId())
                || !identityDirectory.isEligibleStageApprover(command.approverId(), project.provinceId())) {
            throw new BusinessException(ProjectErrorCode.APPROVER_INVALID);
        }
        com.pluto.ndi.shared.approval.ApprovalWorkflow.requireNoPending(
                approvalRepository.existsByProjectIdAndStatus(projectId, ApprovalStatus.PENDING),
                () -> new BusinessException(ProjectErrorCode.PENDING_APPROVAL_EXISTS));
        validateStage(project, command);
        var at = Instant.now(clock);
        var stageOccurredAt = command.stageOccurredOn().atStartOfDay(BUSINESS_ZONE).toInstant();
        var request = ProjectApprovalRequestEntity.stageAdvancement(
                project, command.initiatorLevel(), actorId, command.approverId(), command.targetStage().name(),
                command.evidenceDocumentId(), stageOccurredAt, normalized(command.contractNo()),
                command.signedAmount(), normalized(command.progressContent()), normalized(command.nextAction()), at
        );
        approvalRepository.save(request);
        actionRepository.save(ProjectApprovalActionEntity.create(request.id(), "SUBMITTED", actorId, at, null));
        notificationService.submitted(request, at);
        return ApprovalSummary.from(request);
    }

    public boolean hasBusinessConflict(ProjectEntity project, ProjectApprovalRequestEntity request) {
        if (!ProjectStage.valueOf(request.targetStageCode()).equals(ProjectStage.valueOf(project.currentStageCode()).nextStage())) {
            return true;
        }
        var purpose = evidencePurpose(ProjectStage.valueOf(request.targetStageCode()));
        if (purpose != null && !documentDirectory.isUsable(
                "PROJECT", project.id(), request.evidenceDocumentId(), purpose.name())) {
            return true;
        }
        return request.proposedContractNo() != null
                && projectRepository.existsByContractNoAndIdNot(request.proposedContractNo(), project.id());
    }

    public void applyApproved(ProjectEntity project, ProjectApprovalRequestEntity request, UUID actorId, Instant approvedAt) {
        var target = ProjectStage.valueOf(request.targetStageCode());
        project.advanceStage(target, request.proposedContractNo(), request.proposedSignedAmount(), actorId, approvedAt);
        project.recordAttributionMonth(request.stageOccurredAt());
        milestoneRepository.save(ProjectStageMilestoneEntity.approvedAdvance(
                project.id(), target, actorId, request.stageOccurredAt()));
        if (request.proposedProgressContent() != null) {
            progressRepository.save(ProjectProgressEntity.initial(
                    project.id(), request.proposedProgressContent(), request.proposedNextAction(),
                    null, request.initiatorId(), request.stageOccurredAt(),
                    LocalDate.ofInstant(request.stageOccurredAt(), BUSINESS_ZONE)));
        }
    }

    private void validateStage(ProjectEntity project, StageAdvanceCommand command) {
        var current = ProjectStage.valueOf(project.currentStageCode());
        var previousStageDate = milestoneRepository.findFirstByProjectIdOrderByOccurredAtDesc(project.id())
                .map(milestone -> milestone.occurredAt().atZone(BUSINESS_ZONE).toLocalDate())
                .orElse(project.createdAt().atZone(BUSINESS_ZONE).toLocalDate());
        if (command.targetStage() == null || command.targetStage() != current.nextStage()
                || command.stageOccurredOn() == null
                || command.stageOccurredOn().isAfter(java.time.LocalDate.now(clock.withZone(BUSINESS_ZONE)))
                || command.stageOccurredOn().isBefore(previousStageDate)) {
            throw new BusinessException(ProjectErrorCode.INVALID_STAGE_ADVANCEMENT);
        }
        if ((command.progressContent() == null) != (command.nextAction() == null)
                || command.progressContent() != null
                && (command.progressContent().isBlank() || command.nextAction().isBlank())) {
            throw new BusinessException(ProjectErrorCode.INVALID_STAGE_ADVANCEMENT);
        }
        var purpose = evidencePurpose(command.targetStage());
        if (purpose != null) {
            if (!documentDirectory.isUsable("PROJECT", project.id(), command.evidenceDocumentId(), purpose.name())) {
                throw new BusinessException(ProjectErrorCode.STAGE_EVIDENCE_REQUIRED);
            }
            if (command.contractNo() != null || command.signedAmount() != null) {
                throw new BusinessException(ProjectErrorCode.INVALID_STAGE_ADVANCEMENT);
            }
            return;
        }
        var contractNo = normalized(command.contractNo());
        var amount = command.signedAmount();
        if (command.evidenceDocumentId() != null || contractNo == null || contractNo.length() > 100
                || amount == null || amount.signum() < 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 16) {
            throw new BusinessException(ProjectErrorCode.INVALID_STAGE_ADVANCEMENT);
        }
        if (projectRepository.existsByContractNoAndIdNot(contractNo, project.id())) {
            throw new BusinessException(ProjectErrorCode.CONTRACT_NO_CONFLICT);
        }
    }

    private ProjectDocumentPurpose evidencePurpose(ProjectStage target) {
        return switch (target) {
            case BIDDING -> ProjectDocumentPurpose.CUSTOMER_SOLUTION;
            case SIGNED -> ProjectDocumentPurpose.AWARD_NOTICE;
            case COLLECTION, SOLUTION_EXCHANGE, TERMINATED -> null;
        };
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
