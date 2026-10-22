package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.pluto.ndi.projects.domain.ProjectNarrative;
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
import com.pluto.ndi.projects.repository.ProjectDraftRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.projects.util.ProjectNumberGenerator;
import com.pluto.ndi.projects.util.ProjectExpertNames;
import com.pluto.ndi.projects.util.ProjectPlanningFields;
import com.pluto.ndi.projects.util.ProjectNarrativeText;
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
    private final ProjectDraftRepository draftRepository;
    private final ProjectDuplicateService duplicates;
    private final ProjectCityWriteLock cityWriteLock;
    private final ProjectChangeEventRepository changeEventRepository;
    private final ProjectNumberGenerator projectNumberGenerator;
    private final ProjectMapper projectMapper;
    private final ProjectAccessService projectAccessService;
    private final ProjectMonthlySnapshotService monthlySnapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ProjectInactivityService inactivity;

    public ProjectService(
            IdentityDirectory identityDirectory,
            OrganizationDirectory organizationDirectory,
            ProjectIndustryReferenceRepository industryReferenceRepository,
            ProjectRepository projectRepository,
            ProjectCategoryAmountRepository categoryAmountRepository,
            ProjectStageMilestoneRepository stageMilestoneRepository,
            ProjectProgressRepository progressRepository,
            ProjectDraftRepository draftRepository,
            ProjectDuplicateService duplicates,
            ProjectCityWriteLock cityWriteLock,
            ProjectChangeEventRepository changeEventRepository,
            ProjectNumberGenerator projectNumberGenerator,
            ProjectMapper projectMapper,
            ProjectAccessService projectAccessService,
            ProjectMonthlySnapshotService monthlySnapshots,
            ObjectMapper objectMapper,
            Clock clock,
            ProjectInactivityService inactivity
    ) {
        this.identityDirectory = identityDirectory;
        this.organizationDirectory = organizationDirectory;
        this.industryReferenceRepository = industryReferenceRepository;
        this.projectRepository = projectRepository;
        this.categoryAmountRepository = categoryAmountRepository;
        this.stageMilestoneRepository = stageMilestoneRepository;
        this.progressRepository = progressRepository;
        this.draftRepository = draftRepository;
        this.duplicates = duplicates;
        this.cityWriteLock = cityWriteLock;
        this.changeEventRepository = changeEventRepository;
        this.projectNumberGenerator = projectNumberGenerator;
        this.projectMapper = projectMapper;
        this.projectAccessService = projectAccessService;
        this.monthlySnapshots = monthlySnapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.inactivity = inactivity;
    }

    @Transactional
    public ProjectSummary createProject(UUID actorId, CreateProjectCommand command) {
        var normalized = normalize(command);
        var actor = requireEnabledActor(actorId);
        verifyLocation(normalized);
        verifyCreationScope(actor, normalized);
        verifyProvinceLeader(normalized);
        verifyIndustry(normalized.industryCode());
        if (!List.of("PROVINCE_SIGN", "DS_SIGN").contains(normalized.signingSubjectCode())) {
            throw new BusinessException(ProjectErrorCode.INVALID_SIGNING_SUBJECT);
        }
        var categories = validateCategories(normalized.categoryAmounts(), normalized.totalAmount());
        var draft = normalized.draftId() == null ? null
                : draftRepository.findByIdAndOwnerId(normalized.draftId(), actorId)
                        .orElseThrow(() -> new BusinessException(ProjectErrorCode.DRAFT_NOT_FOUND));
        cityWriteLock.lock(normalized.cityId());
        var duplicate = duplicates.candidates(normalized.cityId(), normalized.projectName());
        if ("STRONG_DUPLICATE".equals(duplicate.status())) {
            throw new BusinessException(ProjectErrorCode.DUPLICATE_PROJECT);
        }
        if ("DUPLICATE_CANDIDATE".equals(duplicate.status())
                && (!normalized.confirmNew() || normalized.duplicateReason() == null
                    || normalized.duplicateReason().isBlank())) {
            throw new BusinessException(ProjectErrorCode.DUPLICATE_CONFIRMATION_REQUIRED);
        }

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
                normalized.signingSubjectCode(),
                actorId,
                occurredAt,
                normalized.importanceCode()
        );
        project.replaceProductSupportExperts(normalized.productSupportExpertNames());
        project.replacePlanningAttributes(ProjectPlanningFields.parseBidMonth(normalized.plannedBidMonth()),
                normalized.applicationScenarioCodes());
        project.replaceNarrative(normalized.narrative());
        projectRepository.saveAndFlush(project);
        inactivity.initialize(project, occurredAt);
        var categoryEntities = categories.stream()
                .map(category -> ProjectCategoryAmountEntity.create(project.id(), category.categoryCode(), category.expectedAmount()))
                .toList();
        monthlySnapshots.recordCreated(project, categoryEntities);
        categoryAmountRepository.saveAll(categoryEntities);
        stageMilestoneRepository.save(ProjectStageMilestoneEntity.initial(project.id(), actorId, occurredAt));
        progressRepository.save(ProjectProgressEntity.initial(
                project.id(), normalized.initialProgress(), normalized.nextAction(),
                normalized.initialProgressOwnerName() == null
                        ? identityDirectory.findProjectUser(normalized.provinceLeaderUserId())
                            .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_PROVINCE_LEADER)).displayName()
                        : normalized.initialProgressOwnerName(), actorId, occurredAt,
                LocalDate.ofInstant(occurredAt, ZoneId.of("Asia/Shanghai"))));
        changeEventRepository.save(projectCreatedEvent(project, actorId, occurredAt,
                "DUPLICATE_CANDIDATE".equals(duplicate.status()) ? normalized.duplicateReason() : null));
        if (draft != null) draftRepository.delete(draft);

        return projectMapper.toSummary(project, categories.stream()
                .map(category -> new ProjectSummary.CategoryAmount(category.categoryCode(), category.expectedAmount()))
                .toList(), identityDirectory.findDisplayNames(List.of(project.provinceLeaderUserId()))
                        .get(project.provinceLeaderUserId()));
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
        // 在分页和权限过滤之后批量查姓名，避免逐项目查目录，也保留停用账号的责任信息。
        var leaderNames = identityDirectory.findDisplayNames(projects.getContent().stream()
                .map(ProjectEntity::provinceLeaderUserId).distinct().toList());
        return projects.map(project -> projectMapper.toSummary(project,
                amountsByProjectId.getOrDefault(project.id(), List.of()), leaderNames.get(project.provinceLeaderUserId())));
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
        // 鉴权后只读取本项目关联的姓名；停用账号不再可选，但既有责任信息仍须展示。
        var leaderName = identityDirectory.findDisplayNames(List.of(project.provinceLeaderUserId()))
                .get(project.provinceLeaderUserId());
        return projectMapper.toDetail(project, categories, leaderName);
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
        var leaderIds = query.provinceLeaderName() == null ? null
                : identityDirectory.findUserIdsByDisplayNameContaining(query.provinceLeaderName());
        return (root, criteria, builder) -> {
            var conditions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (scope.level() == ProjectDataScope.Level.PROVINCE) conditions.add(root.get("provinceId").in(scope.provinceIds()));
            if (scope.level() == ProjectDataScope.Level.CITY) conditions.add(builder.equal(root.get("cityId"), scope.organizationId()));
            if (query.projectCode() != null) {
                conditions.add(builder.equal(root.get("projectCode"), query.projectCode()));
            }
            if (query.projectName() != null) {
                conditions.add(builder.like(root.get("projectName"), "%" + escapeLike(query.projectName()) + "%", '\\'));
            }
            if (query.keyword() != null) {
                var pattern = "%" + escapeLike(query.keyword()) + "%";
                conditions.add(builder.or(builder.like(root.get("projectCode"), pattern, '\\'),
                        builder.like(root.get("projectName"), pattern, '\\')));
            }
            if (query.signingSubjectCode() != null) {
                conditions.add(builder.equal(root.get("signingSubjectCode"), query.signingSubjectCode()));
            }
            if (leaderIds != null) {
                conditions.add(leaderIds.isEmpty() ? builder.disjunction() : root.get("provinceLeaderUserId").in(leaderIds));
            }
            if (query.cityAccountManagerName() != null) {
                conditions.add(builder.like(root.get("cityAccountManagerName"),
                        "%" + escapeLike(query.cityAccountManagerName()) + "%", '\\'));
            }
            if (query.productSupportExpertName() != null) {
                // 通过存在性子查询筛选多位专家，不展开主查询，避免重复行和分页总数膨胀。
                var experts = criteria.subquery(UUID.class);
                var project = experts.from(ProjectEntity.class);
                jakarta.persistence.criteria.Join<ProjectEntity, String> expert = project.join("productSupportExpertNames");
                experts.select(project.get("id")).where(builder.equal(project.get("id"), root.get("id")),
                        builder.like(expert, "%" + escapeLike(query.productSupportExpertName()) + "%", '\\'));
                conditions.add(builder.exists(experts));
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
            var zone = ZoneId.of("Asia/Shanghai");
            if (query.opportunityDateStart() != null) {
                var start = query.opportunityDateStart().atStartOfDay(zone).toInstant();
                if (!start.isBefore(Instant.parse("1000-01-01T00:00:00Z")))
                    conditions.add(builder.greaterThanOrEqualTo(root.get("opportunityDate"), start));
                else conditions.add(builder.isNotNull(root.get("opportunityDate")));
            }
            if (query.opportunityDateEnd() != null) {
                // DATETIME(6) 的日末精度为微秒，避免最大年份加一天越界。
                var end = query.opportunityDateEnd().atTime(23, 59, 59, 999_999_000).atZone(zone).toInstant();
                conditions.add(builder.lessThanOrEqualTo(root.get("opportunityDate"), end));
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
        var importance = command.importanceCode() == null || command.importanceCode().isBlank()
                ? "NORMAL" : command.importanceCode().trim();
        var initialOwner = command.initialProgressOwnerName() == null || command.initialProgressOwnerName().isBlank()
                ? null : command.initialProgressOwnerName().trim();
        if (importance.length() > 30 || initialOwner != null && initialOwner.length() > 100) {
            throw new BusinessException(ProjectErrorCode.INVALID_CREATION_ATTRIBUTES);
        }
        List<String> expertNames;
        List<String> scenarios;
        String bidMonth;
        try {
            expertNames = ProjectExpertNames.normalize(command.productSupportExpertNames());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_SUPPORT_EXPERTS);
        }
        try {
            scenarios = ProjectPlanningFields.normalizeScenarios(command.applicationScenarioCodes());
            var parsedMonth = ProjectPlanningFields.parseBidMonth(command.plannedBidMonth());
            bidMonth = parsedMonth == null ? null : java.time.YearMonth.from(parsedMonth).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_PLANNING_FIELDS);
        }
        var customerNeed = trim(command.customerNeed());
        ProjectNarrative narrative;
        try {
            narrative = ProjectNarrativeText.normalize(command.narrative(), customerNeed);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_NARRATIVE);
        }
        return new CreateProjectCommand(
                command.provinceId(),
                command.cityId(),
                trim(command.projectName()),
                trim(command.customerName()),
                trim(command.industryCode()).toUpperCase(Locale.ROOT),
                customerNeed,
                command.totalAmount(),
                command.provinceLeaderUserId(),
                trim(command.cityAccountManagerName()),
                command.categoryAmounts(),
                trim(command.initialProgress()),
                trim(command.nextAction()),
                command.draftId(),
                command.confirmNew(),
                command.duplicateReason() == null ? null : command.duplicateReason().trim(),
                command.signingSubjectCode() == null || command.signingSubjectCode().isBlank()
                        ? "PROVINCE_SIGN" : command.signingSubjectCode().trim(),
                expertNames, bidMonth, scenarios, narrative, importance, initialOwner
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
            Instant occurredAt,
            String duplicateReason
    ) {
        try {
            var values = new LinkedHashMap<String, Object>();
            values.put("projectCode", project.projectCode());
            values.put("provinceId", project.provinceId().toString());
            values.put("cityId", project.cityId().toString());
            values.put("currentStageCode", project.currentStageCode());
            values.put("totalAmount", project.totalAmount());
            values.put("signingSubjectCode", project.signingSubjectCode());
            values.put("importanceCode", project.importanceCode());
            values.put("productSupportExpertNames", project.productSupportExpertNames());
            values.put("plannedBidMonth", project.plannedBidMonth());
            values.put("applicationScenarioCodes", project.applicationScenarioCodes());
            values.put("constructionContentRaw", project.constructionContentRaw());
            values.put("productPromotionContent", project.productPromotionContent());
            values.put("remark", project.remark());
            if (duplicateReason != null) values.put("duplicateReason", duplicateReason);
            var payload = objectMapper.writeValueAsString(values);
            return ProjectChangeEventEntity.create(project.id(), project.versionNo(), payload, actorId, occurredAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目创建事件无法序列化", exception);
        }
    }

}
