package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageAggregate;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只聚合当前生效主档，历史月末分析留给独立快照读模型。 */
@Service
public class ProjectWorkbenchService {

    private final IdentityDirectory identityDirectory;
    private final ProjectRepository projectRepository;

    public ProjectWorkbenchService(IdentityDirectory identityDirectory, ProjectRepository projectRepository) {
        this.identityDirectory = identityDirectory;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public ProjectWorkbenchOverview overview(UUID actorId, boolean includeTerminated) {
        var actor = identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var scope = identityDirectory.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var aggregates = switch (scope.level()) {
            case PROVINCE -> projectRepository.summarizeCurrentStagesByProvince(
                    scope.organizationId(), includeTerminated);
            case CITY -> projectRepository.summarizeCurrentStagesByCity(
                    scope.organizationId(), includeTerminated);
        };
        return toOverview(aggregates, includeTerminated);
    }

    private ProjectWorkbenchOverview toOverview(
            List<ProjectStageAggregate> aggregates, boolean includeTerminated
    ) {
        var byStage = new EnumMap<ProjectStage, ProjectWorkbenchOverview.StageTotal>(ProjectStage.class);
        for (var aggregate : aggregates) {
            var stage = ProjectStage.valueOf(aggregate.getStageCode());
            byStage.put(stage, new ProjectWorkbenchOverview.StageTotal(
                    stage.name(), aggregate.getProjectCount(), aggregate.getExpectedAmount()));
        }
        var stages = Arrays.stream(ProjectStage.values())
                .filter(stage -> includeTerminated || stage != ProjectStage.TERMINATED)
                .map(stage -> byStage.getOrDefault(stage,
                        new ProjectWorkbenchOverview.StageTotal(stage.name(), 0, BigDecimal.ZERO)))
                .toList();
        var count = stages.stream().mapToLong(ProjectWorkbenchOverview.StageTotal::projectCount).sum();
        var amount = stages.stream().map(ProjectWorkbenchOverview.StageTotal::expectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProjectWorkbenchOverview(includeTerminated, count, amount, stages);
    }
}
