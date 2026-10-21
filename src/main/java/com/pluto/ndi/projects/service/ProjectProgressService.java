package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 进展追加、更正以及项目时间线的受控读取。 */
@Service
public class ProjectProgressService {

    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProjectAccessService accessService;
    private final ProjectProgressRepository progressRepository;
    private final ProjectStageMilestoneRepository milestoneRepository;
    private final Clock clock;
    private final ProjectInactivityService inactivity;

    public ProjectProgressService(
            ProjectAccessService accessService, ProjectProgressRepository progressRepository,
            ProjectStageMilestoneRepository milestoneRepository, Clock clock, ProjectInactivityService inactivity
    ) {
        this.accessService = accessService;
        this.progressRepository = progressRepository;
        this.milestoneRepository = milestoneRepository;
        this.clock = clock;
        this.inactivity = inactivity;
    }

    @Transactional
    public ProjectProgressSummary append(UUID actorId, UUID projectId, AppendProgressCommand command) {
        var project = accessService.requireAppendable(actorId, projectId);
        var content = requiredText(command == null ? null : command.progressContent());
        var nextAction = requiredText(command.nextAction());
        if (command.correctsProgressId() != null) {
            progressRepository.findById(command.correctsProgressId())
                    .filter(progress -> progress.projectId().equals(projectId))
                    .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROGRESS_NOT_FOUND));
        }
        var ownerName = command.ownerName() == null ? null : command.ownerName().trim();
        if (ownerName != null && ownerName.length() > 100) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROGRESS);
        }
        var progressDate = command.progressDate() == null
                ? LocalDate.now(clock.withZone(BUSINESS_ZONE)) : command.progressDate();
        var occurredAt = Instant.now(clock);
        var progress = ProjectProgressEntity.append(
                projectId, content, nextAction, actorId, occurredAt, command.correctsProgressId(),
                progressDate, ownerName == null || ownerName.isEmpty() ? null : ownerName, command.dueDate());
        inactivity.resolveAfterActivity(project, actorId, occurredAt);
        return ProjectProgressSummary.from(progressRepository.save(progress));
    }

    @Transactional(readOnly = true)
    public Page<ProjectProgressSummary> findProgress(UUID actorId, UUID projectId, Pageable pageable) {
        accessService.requireReadable(actorId, projectId);
        return progressRepository.findByProjectIdOrderByOccurredAtDescIdDesc(projectId, pageable)
                .map(ProjectProgressSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<ProjectMilestoneSummary> findMilestones(UUID actorId, UUID projectId, Pageable pageable) {
        accessService.requireReadable(actorId, projectId);
        return milestoneRepository.findByProjectIdOrderByOccurredAtDescIdDesc(projectId, pageable)
                .map(ProjectMilestoneSummary::from);
    }

    private String requiredText(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROGRESS);
        }
        return value.trim();
    }
}
