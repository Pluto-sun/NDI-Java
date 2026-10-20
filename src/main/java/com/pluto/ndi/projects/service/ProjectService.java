package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectCategory;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectCategoryAmountEntity;
import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;
import com.pluto.ndi.projects.mapper.ProjectMapper;
import com.pluto.ndi.projects.repository.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.projects.util.ProjectNumberGenerator;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目创建和查询的首个业务切片。 */
@Service
public class ProjectService {

    private final IdentityDirectory identityDirectory;
    private final OrganizationDirectory organizationDirectory;
    private final ProjectIndustryReferenceRepository industryReferenceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectCategoryAmountRepository categoryAmountRepository;
    private final ProjectStageMilestoneRepository stageMilestoneRepository;
    private final ProjectProgressRepository progressRepository;
    private final ProjectChangeEventRepository changeEventRepository;
    private final ProjectNumberGenerator projectNumberGenerator;
    private final ProjectMapper projectMapper;
    private final ProjectAccessService projectAccessService;
    private final ProjectMonthlySnapshotService monthlySnapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProjectService(
            IdentityDirectory identityDirectory,
            OrganizationDirectory organizationDirectory,
            ProjectIndustryReferenceRepository industryReferenceRepository,
            ProjectRepository projectRepository,
            ProjectCategoryAmountRepository categoryAmountRepository,
            ProjectStageMilestoneRepository stageMilestoneRepository,
            ProjectProgressRepository progressRepository,
            ProjectChangeEventRepository changeEventRepository,
            ProjectNumberGenerator projectNumberGenerator,
            ProjectMapper projectMapper,
            ProjectAccessService projectAccessService,
            ProjectMonthlySnapshotService monthlySnapshots,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.identityDirectory = identityDirectory;
        this.organizationDirectory = organizationDirectory;
        this.industryReferenceRepository = industryReferenceRepository;
        this.projectRepository = projectRepository;
        this.categoryAmountRepository = categoryAmountRepository;
        this.stageMilestoneRepository = stageMilestoneRepository;
        this.progressRepository = progressRepository;
        this.changeEventRepository = changeEventRepository;
        this.projectNumberGenerator = projectNumberGenerator;
        this.projectMapper = projectMapper;
        this.projectAccessService = projectAccessService;
        this.monthlySnapshots = monthlySnapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ProjectSummary createProject(UUID actorId, CreateProjectCommand command) {
        var normalized = normalize(command);
        var actor = requireEnabledActor(actorId);
        verifyLocation(normalized);
        verifyCreationScope(actor, normalized);
        verifyProvinceLeader(normalized);
        verifyIndustry(normalized.industryCode());
        var categories = validateCategories(normalized.categoryAmounts(), normalized.totalAmount());

        var occurredAt = Instant.now(clock);
        var project = ProjectEntity.create(
                UUID.randomUUID(),
                projectNumberGenerator.nextCode(),
                normalized.provinceId(),
                normalized.cityId(),
                normalized.projectName(),
                normalized.customerName(),
                normalized.industryCode(),
                normalized.customerNeed(),
                normalized.totalAmount(),
                normalized.provinceLeaderUserId(),
                normalized.cityAccountManagerName(),
                actorId,
                occurredAt
        );
        projectRepository.saveAndFlush(project);
        monthlySnapshots.recordCreated(project);

        categoryAmountRepository.saveAll(categories.stream()
                .map(category -> ProjectCategoryAmountEntity.create(project.id(), category.categoryCode(), category.expectedAmount()))
                .toList());
        stageMilestoneRepository.save(ProjectStageMilestoneEntity.initial(project.id(), actorId, occurredAt));
        progressRepository.save(ProjectProgressEntity.initial(
                project.id(), normalized.initialProgress(), normalized.nextAction(), actorId, occurredAt));
        changeEventRepository.save(projectCreatedEvent(project, actorId, occurredAt));

        return projectMapper.toSummary(project, categories.stream()
                .map(category -> new ProjectSummary.CategoryAmount(category.categoryCode(), category.expectedAmount()))
                .toList());
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummary> findProjects(UUID actorId, Pageable pageable) {
        return findProjects(actorId, ProjectQuery.all(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummary> findProjects(UUID actorId, ProjectQuery query, Pageable pageable) {
        var scope = readableScope(actorId);
        var projects = projectRepository.findAll(specification(scope, query), pageable);
        var amountsByProjectId = categoryAmountsByProjectId(projects.getContent().stream()
                .map(ProjectEntity::id)
                .toList());
        return projects.map(project -> projectMapper.toSummary(project, amountsByProjectId.getOrDefault(project.id(), List.of())));
    }

    @Transactional(readOnly = true)
    public long countProjects(UUID actorId, ProjectQuery query) {
        return projectRepository.count(specification(readableScope(actorId), query));
    }

    @Transactional(readOnly = true)
    public ProjectDetailSummary findDetail(UUID actorId, UUID projectId) {
        var project = projectAccessService.requireReadable(actorId, projectId);
        var categories = categoryAmountRepository.findByProjectIdIn(List.of(projectId)).stream()
                .map(item -> new ProjectSummary.CategoryAmount(item.categoryCode(), item.expectedAmount()))
                .toList();
        return projectMapper.toDetail(project, categories);
    }

    private ProjectActor requireEnabledActor(UUID actorId) {
        return identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN));
    }

    private ProjectDataScope readableScope(UUID actorId) {
        return identityDirectory.findReadableProjectScope(requireEnabledActor(actorId))
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    private void verifyLocation(CreateProjectCommand command) {
        if (organizationDirectory.findEnabledLocation(command.provinceId(), command.cityId()).isEmpty()) {
            throw new BusinessException(ProjectErrorCode.INVALID_ORGANIZATION);
        }
    }

    private void verifyCreationScope(ProjectActor actor, CreateProjectCommand command) {
        if (!identityDirectory.canCreateProject(actor, command.provinceId(), command.cityId())) {
            throw new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN);
        }
    }

    private void verifyProvinceLeader(CreateProjectCommand command) {
        if (!identityDirectory.isEligibleProvinceLeader(command.provinceLeaderUserId(), command.provinceId())) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROVINCE_LEADER);
        }
    }

    private void verifyIndustry(String industryCode) {
        if (!industryReferenceRepository.existsByIndustryCodeAndEnabledTrue(industryCode)) {
            throw new BusinessException(ProjectErrorCode.INVALID_INDUSTRY);
        }
    }

    private List<CreateProjectCommand.CategoryAmount> validateCategories(
            List<CreateProjectCommand.CategoryAmount> categoryAmounts,
            BigDecimal totalAmount
    ) {
        if (categoryAmounts == null || categoryAmounts.isEmpty() || totalAmount == null || totalAmount.signum() < 0) {
            throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
        }
        var codes = new HashSet<String>();
        var sum = BigDecimal.ZERO;
        for (var item : categoryAmounts) {
            if (item == null || item.categoryCode() == null || item.expectedAmount() == null) {
                throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
            }
            var category = ProjectCategory.fromCode(item.categoryCode()).orElseThrow(
                    () -> new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT));
            if (!codes.add(category.name()) || item.expectedAmount().signum() < 0) {
                throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
            }
            sum = sum.add(item.expectedAmount());
        }
        if (sum.compareTo(totalAmount) > 0) {
            throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
        }
        return categoryAmounts.stream()
                .map(item -> new CreateProjectCommand.CategoryAmount(
                        item.categoryCode().trim().toUpperCase(Locale.ROOT), item.expectedAmount()))
                .toList();
    }

    private Specification<ProjectEntity> specification(ProjectDataScope scope, ProjectQuery query) {
        return (root, ignored, builder) -> {
            var conditions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(builder.equal(root.get(scope.level() == ProjectDataScope.Level.PROVINCE
                    ? "provinceId" : "cityId"), scope.organizationId()));
            if (query.projectCode() != null) {
                conditions.add(builder.equal(root.get("projectCode"), query.projectCode()));
            }
            if (query.projectName() != null) {
                conditions.add(builder.like(root.get("projectName"), "%" + escapeLike(query.projectName()) + "%", '\\'));
            }
            if (query.stage() != null) {
                conditions.add(builder.equal(root.get("currentStageCode"), query.stage().name()));
            }
            if (query.industryCode() != null) {
                conditions.add(builder.equal(root.get("industryCode"), query.industryCode()));
            }
            if (query.provinceId() != null) {
                conditions.add(builder.equal(root.get("provinceId"), query.provinceId()));
            }
            if (query.cityId() != null) {
                conditions.add(builder.equal(root.get("cityId"), query.cityId()));
            }
            if (!query.includeTerminated()) {
                conditions.add(builder.notEqual(root.get("currentStageCode"), ProjectStage.TERMINATED.name()));
            }
            return builder.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<UUID, List<ProjectSummary.CategoryAmount>> categoryAmountsByProjectId(Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return categoryAmountRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(
                        ProjectCategoryAmountEntity::projectId,
                        Collectors.mapping(
                                amount -> new ProjectSummary.CategoryAmount(amount.categoryCode(), amount.expectedAmount()),
                                Collectors.toList())
                ));
    }

    private CreateProjectCommand normalize(CreateProjectCommand command) {
        return new CreateProjectCommand(
                command.provinceId(),
                command.cityId(),
                trim(command.projectName()),
                trim(command.customerName()),
                trim(command.industryCode()).toUpperCase(Locale.ROOT),
                trim(command.customerNeed()),
                command.totalAmount(),
                command.provinceLeaderUserId(),
                trim(command.cityAccountManagerName()),
                command.categoryAmounts(),
                trim(command.initialProgress()),
                trim(command.nextAction())
        );
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("项目创建字段不得为空");
        }
        return value.trim();
    }

    private ProjectChangeEventEntity projectCreatedEvent(
            ProjectEntity project,
            UUID actorId,
            Instant occurredAt
    ) {
        try {
            var payload = objectMapper.writeValueAsString(Map.of(
                    "projectCode", project.projectCode(),
                    "provinceId", project.provinceId().toString(),
                    "cityId", project.cityId().toString(),
                    "currentStageCode", project.currentStageCode(),
                    "totalAmount", project.totalAmount()
            ));
            return ProjectChangeEventEntity.create(project.id(), project.versionNo(), payload, actorId, occurredAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目创建事件无法序列化", exception);
        }
    }

}
