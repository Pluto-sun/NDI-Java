package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalActionEntity;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;
import com.pluto.ndi.projects.repository.ProjectApprovalActionRepository;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目审批与正式数据生效边界。 */
@Service
public class ProjectApprovalService {

    private final IdentityDirectory identityDirectory;
    private final ProjectRepository projectRepository;
    private final ProjectCategoryAmountRepository categoryAmountRepository;
    private final ProjectApprovalRequestRepository approvalRepository;
    private final ProjectApprovalActionRepository actionRepository;
    private final ProjectStageMilestoneRepository milestoneRepository;
    private final ProjectChangeEventRepository eventRepository;
    private final ProjectStageService stageService;
    private final ProjectApprovalNotificationService notificationService;
    private final ProjectMonthlySnapshotService monthlySnapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProjectApprovalService(
            IdentityDirectory identityDirectory, ProjectRepository projectRepository,
            ProjectCategoryAmountRepository categoryAmountRepository,
            ProjectApprovalRequestRepository approvalRepository,
            ProjectApprovalActionRepository actionRepository,
            ProjectStageMilestoneRepository milestoneRepository,
            ProjectChangeEventRepository eventRepository, ProjectStageService stageService,
            ProjectApprovalNotificationService notificationService,
            ProjectMonthlySnapshotService monthlySnapshots,
            ObjectMapper objectMapper, Clock clock
    ) {
        this.identityDirectory = identityDirectory;
        this.projectRepository = projectRepository;
        this.categoryAmountRepository = categoryAmountRepository;
        this.approvalRepository = approvalRepository;
        this.actionRepository = actionRepository;
        this.milestoneRepository = milestoneRepository;
        this.eventRepository = eventRepository;
        this.stageService = stageService;
        this.notificationService = notificationService;
        this.monthlySnapshots = monthlySnapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ApprovalSummary submitMajorChange(UUID actorId, UUID projectId, MajorChangeCommand command) {
        var project = lockedProject(projectId);
        var actor = enabledActor(actorId);
        if (command == null || command.changeType() == null || command.initiatorLevel() == null
                || !identityDirectory.canInitiateMajorChange(
                        actor, project.provinceId(), project.cityId(), command.initiatorLevel())) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        if (ProjectStage.TERMINATED.name().equals(project.currentStageCode())) {
            throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
        }
        if (command.approverId() == null || actorId.equals(command.approverId())
                || !identityDirectory.isEligibleMajorChangeApprover(
                        command.approverId(), project.provinceId(), command.initiatorLevel())) {
            throw new BusinessException(ProjectErrorCode.APPROVER_INVALID);
        }
        if (approvalRepository.existsByProjectIdAndStatus(projectId, ApprovalStatus.PENDING)) {
            throw new BusinessException(ProjectErrorCode.PENDING_APPROVAL_EXISTS);
        }
        var reason = requiredText(command.reason(), 2000);
        validateChange(project, command);
        var at = Instant.now(clock);
        var request = ProjectApprovalRequestEntity.majorChange(
                command.changeType(), command.initiatorLevel(), actorId, command.approverId(),
                project,
                command.targetStage() == null ? null : command.targetStage().name(),
                command.projectName() == null ? null : command.projectName().trim(), command.totalAmount(), reason, at
        );
        approvalRepository.save(request);
        actionRepository.save(ProjectApprovalActionEntity.create(request.id(), "SUBMITTED", actorId, at, null));
        notificationService.submitted(request, at);
        return ApprovalSummary.from(request);
    }

    @Transactional(readOnly = true)
    public ApprovalSummary findApproval(UUID actorId, UUID requestId) {
        var request = approvalRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.APPROVAL_NOT_FOUND));
        if (!actorId.equals(request.initiatorId()) && !actorId.equals(request.approverId())) {
            throw new BusinessException(ProjectErrorCode.APPROVAL_FORBIDDEN);
        }
        enabledActor(actorId);
        return ApprovalSummary.from(request);
    }

    @Transactional
    public ApprovalSummary withdraw(UUID actorId, UUID requestId) {
        var request = pendingRequestAfterProjectLock(requestId).request();
        if (!actorId.equals(request.initiatorId())) {
            throw new BusinessException(ProjectErrorCode.APPROVAL_FORBIDDEN);
        }
        enabledActor(actorId);
        var at = Instant.now(clock);
        request.finish(ApprovalStatus.WITHDRAWN, null, at);
        actionRepository.save(ProjectApprovalActionEntity.create(requestId, "WITHDRAWN", actorId, at, null));
        notificationService.finished(request, ApprovalNotificationKind.WITHDRAWN, at);
        return ApprovalSummary.from(request);
    }

    @Transactional
    public ApprovalSummary decide(UUID actorId, UUID requestId, boolean approved, String opinion) {
        var locked = pendingRequestAfterProjectLock(requestId);
        var request = locked.request();
        var project = locked.project();
        if (!actorId.equals(request.approverId()) || !isEligibleApprover(actorId, project, request)) {
            throw new BusinessException(ProjectErrorCode.APPROVAL_FORBIDDEN);
        }
        if (!approved && (opinion == null || opinion.isBlank())) {
            throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
        }
        var at = Instant.now(clock);
        if (project.versionNo() != request.baseVersion()
                || approved && "STAGE_ADVANCEMENT".equals(request.requestType())
                && stageService.hasBusinessConflict(project, request)) {
            request.finish(ApprovalStatus.CONFLICT, null, at);
            actionRepository.save(ProjectApprovalActionEntity.create(requestId, "CONFLICT", actorId, at, null));
            notificationService.finished(request, ApprovalNotificationKind.CONFLICT, at);
            return ApprovalSummary.from(request);
        }
        if (approved) {
            monthlySnapshots.ensureBaseline(project);
            var before = projectSnapshot(project);
            if ("STAGE_ADVANCEMENT".equals(request.requestType())) {
                stageService.applyApproved(project, request, actorId, at);
            } else {
                applyChange(project, request, actorId, at);
            }
            projectRepository.saveAndFlush(project);
            eventRepository.save(approvedEvent(project, request, before, actorId, at));
            monthlySnapshots.recordApproved(project, request);
        }
        var status = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
        request.finish(status, opinion == null ? null : opinion.trim(), at);
        actionRepository.save(ProjectApprovalActionEntity.create(requestId, status.name(), actorId, at, request.reviewOpinion()));
        notificationService.finished(request,
                approved ? ApprovalNotificationKind.APPROVED : ApprovalNotificationKind.REJECTED, at);
        return ApprovalSummary.from(request);
    }

    private LockedApproval pendingRequestAfterProjectLock(UUID requestId) {
        var projectId = approvalRepository.findProjectIdById(requestId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.APPROVAL_NOT_FOUND));
        var project = lockedProject(projectId);
        var request = approvalRepository.findLockedById(requestId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.APPROVAL_NOT_FOUND));
        if (request.status() != ApprovalStatus.PENDING) {
            throw new BusinessException(ProjectErrorCode.APPROVAL_NOT_PENDING);
        }
        return new LockedApproval(project, request);
    }

    private ProjectEntity lockedProject(UUID projectId) {
        return projectRepository.findLockedById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
    }

    private boolean isEligibleApprover(UUID actorId, ProjectEntity project, ProjectApprovalRequestEntity request) {
        return switch (request.requestType()) {
            case "MAJOR_CHANGE" -> identityDirectory.isEligibleMajorChangeApprover(
                    actorId, project.provinceId(), request.initiatorLevel());
            case "STAGE_ADVANCEMENT" -> identityDirectory.isEligibleStageApprover(actorId, project.provinceId());
            default -> false;
        };
    }

    private ProjectActor enabledActor(UUID actorId) {
        return identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    private void validateChange(ProjectEntity project, MajorChangeCommand command) {
        switch (command.changeType()) {
            case PROJECT_NAME -> {
                var name = requiredText(command.projectName(), 200);
                if (name.equals(project.projectName())) {
                    throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
                }
            }
            case TOTAL_AMOUNT -> {
                var amount = command.totalAmount();
                if (amount == null || amount.signum() < 0 || amount.scale() > 2
                        || amount.compareTo(project.totalAmount()) == 0) {
                    throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
                }
                var categorySum = categoryAmountRepository.findByProjectIdIn(java.util.List.of(project.id())).stream()
                        .map(item -> item.expectedAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (categorySum.compareTo(amount) > 0) {
                    throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
                }
            }
            case STAGE_ROLLBACK -> {
                var target = command.targetStage();
                var current = ProjectStage.valueOf(project.currentStageCode());
                if (target == null || !target.isBefore(current)) {
                    throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
                }
            }
            case TERMINATE -> {
                if (command.targetStage() != null && command.targetStage() != ProjectStage.TERMINATED) {
                    throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
                }
            }
        }
        if (command.changeType() != MajorChangeType.PROJECT_NAME && command.projectName() != null
                || command.changeType() != MajorChangeType.TOTAL_AMOUNT && command.totalAmount() != null
                || command.changeType() != MajorChangeType.STAGE_ROLLBACK
                && command.changeType() != MajorChangeType.TERMINATE && command.targetStage() != null) {
            throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
        }
    }

    private void applyChange(ProjectEntity project, ProjectApprovalRequestEntity request, UUID actorId, Instant at) {
        switch (request.changeType()) {
            case PROJECT_NAME -> project.rename(request.proposedProjectName(), actorId, at);
            case TOTAL_AMOUNT -> project.changeTotalAmount(request.proposedTotalAmount(), actorId, at);
            case STAGE_ROLLBACK, TERMINATE -> {
                var target = request.changeType() == MajorChangeType.TERMINATE
                        ? ProjectStage.TERMINATED : ProjectStage.valueOf(request.targetStageCode());
                project.changeStage(target, actorId, at);
                milestoneRepository.save(ProjectStageMilestoneEntity.approvedChange(project.id(), target, actorId, at));
            }
        }
    }

    private ProjectChangeEventEntity approvedEvent(
            ProjectEntity project, ProjectApprovalRequestEntity request, Map<String, Object> before,
            UUID actorId, Instant at
    ) {
        try {
            var event = new LinkedHashMap<String, Object>();
            event.put("approvalRequestId", request.id().toString());
            event.put("requestType", request.requestType());
            event.put("changeType", request.changeType() == null ? null : request.changeType().name());
            event.put("reason", request.changeReason());
            event.put("evidenceDocumentId", request.evidenceDocumentId());
            event.put("stageOccurredAt", request.stageOccurredAt());
            event.put("before", before);
            event.put("after", projectSnapshot(project));
            var payload = objectMapper.writeValueAsString(event);
            return ProjectChangeEventEntity.approvedChange(
                    project.id(), project.versionNo(), request.requestType() + "_APPROVED", payload, actorId, at);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目审批事件无法序列化", exception);
        }
    }

    private Map<String, Object> projectSnapshot(ProjectEntity project) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("projectName", project.projectName());
        snapshot.put("totalAmount", project.totalAmount());
        snapshot.put("stageCode", project.currentStageCode());
        snapshot.put("contractNo", project.contractNo());
        snapshot.put("signedAmount", project.signedAmount());
        return snapshot;
    }

    private String requiredText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessException(ProjectErrorCode.INVALID_MAJOR_CHANGE);
        }
        return value.trim();
    }

    private record LockedApproval(ProjectEntity project, ProjectApprovalRequestEntity request) {
    }
}
