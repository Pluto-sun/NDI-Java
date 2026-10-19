package com.pluto.ndi.projects.application;

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
import com.pluto.ndi.identity.IdentityDirectory;
import com.pluto.ndi.identity.ProjectActor;
import com.pluto.ndi.organization.OrganizationDirectory;
import com.pluto.ndi.projects.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectCategory;
import com.pluto.ndi.projects.domain.ProjectNumberGenerator;
import com.pluto.ndi.projects.infrastructure.ProjectCategoryAmountEntity;
import com.pluto.ndi.projects.infrastructure.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.infrastructure.ProjectChangeEventEntity;
import com.pluto.ndi.projects.infrastructure.ProjectChangeEventRepository;
import com.pluto.ndi.projects.infrastructure.ProjectEntity;
import com.pluto.ndi.projects.infrastructure.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.infrastructure.ProjectProgressEntity;
import com.pluto.ndi.projects.infrastructure.ProjectProgressRepository;
import com.pluto.ndi.projects.infrastructure.ProjectRepository;
import com.pluto.ndi.projects.infrastructure.ProjectStageMilestoneEntity;
import com.pluto.ndi.projects.infrastructure.ProjectStageMilestoneRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目创建和查询的首个业务切片。 */
@Service
public class ProjectApplicationService {

    private final IdentityDirectory identityDirectory;
    private final OrganizationDirectory organizationDirectory;
    private final ProjectIndustryReferenceRepository industryReferenceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectCategoryAmountRepository categoryAmountRepository;
    private final ProjectStageMilestoneRepository stageMilestoneRepository;
    private final ProjectProgressRepository progressRepository;
    private final ProjectChangeEventRepository changeEventRepository;
    private final ProjectNumberGenerator projectNumberGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProjectApplicationService(
            IdentityDirectory identityDirectory,
            OrganizationDirectory organizationDirectory,
            ProjectIndustryReferenceRepository industryReferenceRepository,
            ProjectRepository projectRepository,
            ProjectCategoryAmountRepository categoryAmountRepository,
            ProjectStageMilestoneRepository stageMilestoneRepository,
            ProjectProgressRepository progressRepository,
            ProjectChangeEventRepository changeEventRepository,
            ProjectNumberGenerator projectNumberGenerator,
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
        projectRepository.save(project);

        categoryAmountRepository.saveAll(categories.stream()
                .map(category -> ProjectCategoryAmountEntity.create(project.id(), category.categoryCode(), category.expectedAmount()))
                .toList());
        stageMilestoneRepository.save(ProjectStageMilestoneEntity.initial(project.id(), actorId, occurredAt));
        progressRepository.save(ProjectProgressEntity.initial(
                project.id(), normalized.initialProgress(), normalized.nextAction(), actorId, occurredAt));
        changeEventRepository.save(projectCreatedEvent(project, actorId, occurredAt));

        return toSummary(project, categories.stream()
                .map(category -> new ProjectSummary.CategoryAmount(category.categoryCode(), category.expectedAmount()))
                .toList());
    }

    @Transactional(readOnly = true)
    public Page<ProjectSummary> findProjects(UUID actorId, Pageable pageable) {
        var actor = requireEnabledActor(actorId);
        var projects = findProjectsInScope(actor, pageable);
        var amountsByProjectId = categoryAmountsByProjectId(projects.getContent().stream()
                .map(ProjectEntity::id)
                .toList());
        return projects.map(project -> toSummary(project, amountsByProjectId.getOrDefault(project.id(), List.of())));
    }

    private ProjectActor requireEnabledActor(UUID actorId) {
        return identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN));
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

    private Page<ProjectEntity> findProjectsInScope(ProjectActor actor, Pageable pageable) {
        var scope = identityDirectory.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return switch (scope.level()) {
            case PROVINCE -> projectRepository.findByProvinceId(scope.organizationId(), pageable);
            case CITY -> projectRepository.findByCityId(scope.organizationId(), pageable);
        };
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

    private ProjectSummary toSummary(ProjectEntity project, List<ProjectSummary.CategoryAmount> categories) {
        return new ProjectSummary(
                project.id(),
                project.projectCode(),
                project.projectName(),
                project.customerName(),
                project.industryCode(),
                project.totalAmount(),
                project.currentStageCode(),
                categories,
                project.createdAt()
        );
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
