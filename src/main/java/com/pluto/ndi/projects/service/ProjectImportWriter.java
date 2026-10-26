package com.pluto.ndi.projects.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectCategoryAmountEntity;
import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import com.pluto.ndi.projects.domain.entity.ProjectSourceRecordEntity;
import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;
import com.pluto.ndi.projects.repository.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectImportRowRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectSourceRecordRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.projects.util.ProjectNameMatcher;
import com.pluto.ndi.projects.util.ProjectNarrativeText;
import com.pluto.ndi.projects.util.ProjectNumberGenerator;
import com.pluto.ndi.projects.util.ProjectPlanningFields;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** 每行在独立事务内重验并写入，不能绕过当前权限与参考数据。 */
@Component
public class ProjectImportWriter {
    private final IdentityDirectory identity;
    private final OrganizationDirectory organizations;
    private final ProjectIndustryReferenceRepository industries;
    private final ProjectRepository projects;
    private final ProjectCategoryAmountRepository categories;
    private final ProjectProgressRepository progress;
    private final ProjectStageMilestoneRepository milestones;
    private final ProjectChangeEventRepository events;
    private final ProjectSourceRecordRepository sources;
    private final ProjectImportRowRepository rows;
    private final ProjectNumberGenerator numbers;
    private final ObjectMapper json;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final ProjectCityWriteLock cityWriteLock;
    private final ProjectMonthlySnapshotService monthlySnapshots;
    private final ProjectInactivityService inactivity;
    private final ProjectResponsiblePersonStore people;
    private final ProjectImportNameReviewService nameReviews;

    public ProjectImportWriter(
            IdentityDirectory identity,
            OrganizationDirectory organizations,
            ProjectIndustryReferenceRepository industries,
            ProjectRepository projects,
            ProjectCategoryAmountRepository categories,
            ProjectProgressRepository progress,
            ProjectStageMilestoneRepository milestones,
            ProjectChangeEventRepository events,
            ProjectSourceRecordRepository sources,
            ProjectImportRowRepository rows,
            ProjectNumberGenerator numbers,
            ObjectMapper json,
            Clock clock,
            JdbcTemplate jdbc,
            ProjectCityWriteLock cityWriteLock,
            ProjectMonthlySnapshotService monthlySnapshots,
            ProjectInactivityService inactivity,
            ProjectResponsiblePersonStore people,
            ProjectImportNameReviewService nameReviews) {
        this.identity = identity;
        this.organizations = organizations;
        this.industries = industries;
        this.projects = projects;
        this.categories = categories;
        this.progress = progress;
        this.milestones = milestones;
        this.events = events;
        this.sources = sources;
        this.rows = rows;
        this.numbers = numbers;
        this.json = json;
        this.clock = clock;
        this.jdbc = jdbc;
        this.cityWriteLock = cityWriteLock;
        this.monthlySnapshots = monthlySnapshots;
        this.inactivity = inactivity;
        this.people = people;
        this.nameReviews = nameReviews;
    }

    public boolean importRow(UUID actorId, UUID rowId) {
        try {
            var row = rows.lockById(rowId).orElseThrow();
            if (!"READY".equals(row.status())) return false;
            var candidate = json.readValue(row.rowPayload(), ProjectImportCandidate.class);
            var actor =
                    identity.findProjectActor(actorId).filter(ProjectActor::enabled).orElseThrow();
            if (!identity.canImportProject(actor, candidate.provinceId(), candidate.cityId())) {
                throw new IllegalStateException("项目导入授权已变化");
            }
            if (organizations
                            .findEnabledLocation(candidate.provinceId(), candidate.cityId())
                            .isEmpty()
                    || candidate.provinceLeaderUserId() != null
                            && !identity.isEligibleProvinceLeader(
                                    candidate.provinceLeaderUserId(), candidate.provinceId())
                    || !industries.existsByIndustryCodeAndEnabledTrue(candidate.industryCode())) {
                throw new IllegalStateException("组织、行业或责任账号已变化");
            }
            byte[] cityKey = cityWriteLock.binary(candidate.cityId());
            cityWriteLock.lock(candidate.cityId());
            nameReviews.verifyLive(row, candidate.cityId(), candidate.projectName());
            var existingNames =
                    jdbc.query(
                            "SELECT project_name FROM project_project WHERE city_id = ?",
                            (result, index) -> result.getString(1),
                            cityKey);
            var nameReview = nameReviews.read(row);
            if ((nameReview == null || !nameReview.accepted())
                    && existingNames.stream()
                            .anyMatch(
                                    name ->
                                            ProjectNameMatcher.standard(name)
                                                    .equals(
                                                            ProjectNameMatcher.standard(
                                                                    candidate.projectName())))) {
                throw new IllegalStateException("目标库已有同名项目");
            }
            // 预检之后仍可能有其他批次或审批占用合同编号，写入时再次校验；唯一索引防护并发。
            if ("COLLECTION".equals(candidate.stageCode())
                    && (candidate.contractNo() == null || candidate.signedAmount() == null)) {
                throw new IllegalStateException("计收阶段必须填写合同编号和签约金额");
            }
            if (candidate.contractNo() != null
                    && projects.existsByContractNo(candidate.contractNo())) {
                throw new IllegalStateException("合同编号已存在");
            }
            Instant now = Instant.now(clock);
            UUID id = UUID.randomUUID();
            var project =
                    ProjectEntity.imported(
                            id,
                            numbers.nextCode(),
                            candidate.provinceId(),
                            candidate.cityId(),
                            candidate.projectName(),
                            candidate.customerName(),
                            candidate.industryCode(),
                            candidate.customerNeed(),
                            candidate.totalAmount(),
                            candidate.provinceLeaderUserId(),
                            candidate.cityAccountManagerName(),
                            ProjectStage.valueOf(candidate.stageCode()),
                            candidate.contractNo(),
                            candidate.signedAmount(),
                            candidate.signingSubjectCode() == null
                                    ? "PROVINCE_SIGN"
                                    : candidate.signingSubjectCode(),
                            actorId,
                            candidate.occurredAt());
            project.recordOpportunityDate(now);
            project.initializeImportAttributes(
                    candidate.importAttributes() == null
                            ? new com.pluto.ndi.projects.domain.ProjectImportAttributes(null, null)
                            : candidate.importAttributes());
            project.recordAttributionMonth(now);
            project.replaceProductSupportExperts(
                    candidate.productSupportExpertNames() == null
                            ? java.util.List.of()
                            : candidate.productSupportExpertNames());
            project.replacePlanningAttributes(
                    ProjectPlanningFields.parseBidMonth(candidate.plannedBidMonth()),
                    ProjectPlanningFields.normalizeScenarios(candidate.applicationScenarioCodes()));
            project.replaceNarrative(
                    ProjectNarrativeText.normalize(
                            candidate.narrative(), candidate.customerNeed()));
            projects.saveAndFlush(project);
            people.initialize(
                    project,
                    candidate.responsiblePersonNames(),
                    candidate.legacyProvinceLeaderName());
            var lastActivityAt =
                    candidate.progress().stream()
                            .map(item -> item.occurredAt())
                            .max(Instant::compareTo)
                            .filter(at -> at.isAfter(project.updatedAt()))
                            .orElse(project.updatedAt());
            inactivity.initialize(project, lastActivityAt);
            var categoryEntities =
                    candidate.categories().stream()
                            .map(
                                    category ->
                                            ProjectCategoryAmountEntity.create(
                                                    id, category.code(), category.amount()))
                            .toList();
            monthlySnapshots.recordCreated(project, categoryEntities);
            categories.saveAll(categoryEntities);
            milestones.save(
                    ProjectStageMilestoneEntity.imported(
                            id,
                            ProjectStage.valueOf(candidate.stageCode()),
                            actorId,
                            candidate.occurredAt(),
                            "EXCEL_IMPORT",
                            "Excel 导入阶段"));
            for (var item : candidate.progress()) {
                progress.save(
                        ProjectProgressEntity.imported(
                                id,
                                item.content(),
                                "",
                                candidate.cityAccountManagerName(),
                                null,
                                LocalDate.ofInstant(item.occurredAt(), ZoneId.of("Asia/Shanghai")),
                                actorId,
                                item.occurredAt()));
            }
            sources.save(
                    ProjectSourceRecordEntity.of(
                            id, "EXCEL_IMPORT", row.id().toString(), "EXCEL_IMPORT", now));
            events.save(
                    ProjectChangeEventEntity.create(
                            id, 0, "{\"source\":\"EXCEL_IMPORT\"}", actorId, now));
            row.imported(id);
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("导入行解析失败", exception);
        }
    }
}
