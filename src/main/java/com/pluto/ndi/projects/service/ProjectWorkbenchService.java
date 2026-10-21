package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectIndustryReferenceEntity;
import com.pluto.ndi.projects.repository.ProjectCityAggregate;
import com.pluto.ndi.projects.repository.ProjectIndustryAggregate;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageAggregate;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只聚合当前生效主档，历史月末分析留给独立快照读模型。 */
@Service
public class ProjectWorkbenchService {

    private final IdentityDirectory identityDirectory;
    private final OrganizationDirectory organizationDirectory;
    private final ProjectRepository projectRepository;
    private final ProjectIndustryReferenceRepository industryRepository;
    private final ProjectApprovalInboxService inboxService;

    public ProjectWorkbenchService(IdentityDirectory identityDirectory,
            OrganizationDirectory organizationDirectory, ProjectRepository projectRepository,
            ProjectIndustryReferenceRepository industryRepository, ProjectApprovalInboxService inboxService) {
        this.identityDirectory = identityDirectory;
        this.organizationDirectory = organizationDirectory;
        this.projectRepository = projectRepository;
        this.industryRepository = industryRepository;
        this.inboxService = inboxService;
    }

    @Transactional(readOnly = true)
    public ProjectWorkbenchOverview overview(UUID actorId, boolean includeTerminated) {
        var scope = readableScope(actorId);
        var aggregates = switch (scope.level()) {
            case NATIONAL -> projectRepository.summarizeCurrentStagesNational(includeTerminated);
            case PROVINCE -> projectRepository.summarizeCurrentStagesByProvinces(
                    scope.provinceIds(), includeTerminated);
            case CITY -> projectRepository.summarizeCurrentStagesByCity(
                    scope.organizationId(), includeTerminated);
        };
        return toOverview(aggregates, includeTerminated, inboxService.counts(actorId));
    }

    @Transactional(readOnly = true)
    public ProjectWorkbenchBreakdown breakdown(UUID actorId, boolean includeTerminated) {
        var scope = readableScope(actorId);
        List<ProjectCityAggregate> cityAggregates;
        List<ProjectIndustryAggregate> industryAggregates;
        switch (scope.level()) {
            case NATIONAL -> {
                cityAggregates = projectRepository.summarizeCurrentCitiesNational(includeTerminated);
                industryAggregates = projectRepository.summarizeCurrentIndustriesNational(includeTerminated);
            }
            case PROVINCE -> {
                cityAggregates = projectRepository.summarizeCurrentCitiesByProvinces(
                        scope.provinceIds(), includeTerminated);
                industryAggregates = projectRepository.summarizeCurrentIndustriesByProvinces(
                        scope.provinceIds(), includeTerminated);
            }
            case CITY -> {
                cityAggregates = projectRepository.summarizeCurrentCitiesByCity(
                        scope.organizationId(), includeTerminated);
                industryAggregates = projectRepository.summarizeCurrentIndustriesByCity(
                        scope.organizationId(), includeTerminated);
            }
            default -> throw new IllegalStateException("未支持的项目数据范围");
        }
        var cityNames = organizationDirectory.findCityNames(cityAggregates.stream()
                .map(ProjectCityAggregate::getCityId).toList());
        Map<String, String> industryNames = industryRepository.findAllById(industryAggregates.stream()
                .map(ProjectIndustryAggregate::getIndustryCode).toList()).stream()
                .collect(Collectors.toMap(ProjectIndustryReferenceEntity::industryCode,
                        ProjectIndustryReferenceEntity::industryName));
        var cities = cityAggregates.stream()
                .map(item -> new ProjectWorkbenchBreakdown.CityTotal(item.getCityId(),
                        cityNames.getOrDefault(item.getCityId(), item.getCityId().toString()),
                        item.getProjectCount(), item.getExpectedAmount()))
                .sorted(Comparator.comparing(ProjectWorkbenchBreakdown.CityTotal::expectedAmount).reversed()
                        .thenComparing(item -> item.cityId().toString())).toList();
        var industries = industryAggregates.stream()
                .map(item -> new ProjectWorkbenchBreakdown.IndustryTotal(item.getIndustryCode(),
                        industryNames.getOrDefault(item.getIndustryCode(), item.getIndustryCode()),
                        item.getProjectCount(), item.getExpectedAmount()))
                .sorted(Comparator.comparing(ProjectWorkbenchBreakdown.IndustryTotal::expectedAmount).reversed()
                        .thenComparing(ProjectWorkbenchBreakdown.IndustryTotal::industryCode)).toList();
        return new ProjectWorkbenchBreakdown(includeTerminated, cities, industries);
    }

    private ProjectDataScope readableScope(UUID actorId) {
        var actor = identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return identityDirectory.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    private ProjectWorkbenchOverview toOverview(
            List<ProjectStageAggregate> aggregates, boolean includeTerminated, ApprovalInboxCounts inbox
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
        return new ProjectWorkbenchOverview(includeTerminated, count, amount, stages,
                new ProjectWorkbenchOverview.ApprovalEntry(inbox.pendingCount(), inbox.overdueCount(),
                        "/api/v1/projects/approvals/inbox"));
    }
}
