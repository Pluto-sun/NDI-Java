package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectCategoryTotals;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectMonthlyRevision;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import com.pluto.ndi.projects.domain.entity.ProjectCategoryAmountEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.repository.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectMonthlySnapshotRepository;
import com.pluto.ndi.projects.repository.ProjectMonthlySnapshotRepository.Event;
import com.pluto.ndi.projects.repository.ProjectMonthlySnapshotRepository.Snapshot;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 维护可追溯的月末项目状态，并只修订受迟到审批影响的项目。 */
@Service
public class ProjectMonthlySnapshotService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProjectMonthlySnapshotRepository snapshots;
    private final ProjectRepository projects;
    private final ProjectCategoryAmountRepository categories;
    private final OrganizationDirectory organizations;
    private final ProjectIndustryReferenceRepository industries;
    private final IdentityDirectory identities;
    private final ProjectAccessService access;
    private final Clock clock;
    private final TransactionTemplate monthTransactions;

    public ProjectMonthlySnapshotService(ProjectMonthlySnapshotRepository snapshots, ProjectRepository projects,
            ProjectCategoryAmountRepository categories,
            OrganizationDirectory organizations, ProjectIndustryReferenceRepository industries,
            IdentityDirectory identities, ProjectAccessService access,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.snapshots = snapshots;
        this.projects = projects;
        this.categories = categories;
        this.organizations = organizations;
        this.industries = industries;
        this.identities = identities;
        this.access = access;
        this.clock = clock;
        this.monthTransactions = new TransactionTemplate(transactionManager);
        this.monthTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** 首次启用时保存现有主档作为起始状态；启用前月份不作历史还原。 */
    @Transactional
    public void initialize() {
        snapshots.initialize(currentMonth());
        var pageNumber = 0;
        while (true) {
            var page = projects.findAll(PageRequest.of(pageNumber++, 500, Sort.by("id")));
            var amountByProject = categories.findByProjectIdIn(page.getContent().stream()
                    .map(ProjectEntity::id).toList()).stream()
                    .collect(Collectors.groupingBy(ProjectCategoryAmountEntity::projectId));
            for (var project : page.getContent()) {
                ensureBaseline(project, ProjectCategoryTotals.from(
                        amountByProject.getOrDefault(project.id(), List.of())));
            }
            if (!page.hasNext()) break;
        }
    }

    public void ensureBaseline(ProjectEntity project) {
        if (snapshots.hasBaseline(project.id())) return;
        ensureBaseline(project, ProjectCategoryTotals.from(categories.findByProjectIdIn(List.of(project.id()))));
    }

    private void ensureBaseline(ProjectEntity project, ProjectCategoryTotals categoryTotals) {
        if (snapshots.hasBaseline(project.id())) return;
        var month = snapshots.activationMonth();
        snapshots.insertEvent(new Event(project.id(), month, Instant.now(clock), "BASELINE", project.id(),
                "项目月末快照起始状态", true, project.provinceId(), project.cityId(),
                project.industryCode(), project.currentStageCode(), project.totalAmount(), categoryTotals));
    }

    public void recordCreated(ProjectEntity project, List<ProjectCategoryAmountEntity> amounts) {
        snapshots.insertEvent(new Event(project.id(), currentMonth(), Instant.now(clock),
                "BASELINE", project.id(), "项目创建或导入", true, project.provinceId(), project.cityId(),
                project.industryCode(), project.currentStageCode(), project.totalAmount(),
                ProjectCategoryTotals.from(amounts)));
    }

    public void recordProfileIndustry(ProjectEntity project, UUID sourceId, String formerIndustry) {
        if (Objects.equals(formerIndustry, project.industryCode())) return;
        snapshots.insertEvent(new Event(project.id(), currentMonth(), Instant.now(clock), "PROFILE", sourceId,
                "客户所属行业维护", false, null, null, project.industryCode(), null, null, null));
    }

    public void recordApproved(ProjectEntity project, ProjectApprovalRequestEntity request) {
        var stage = "STAGE_ADVANCEMENT".equals(request.requestType())
                || request.changeType() == MajorChangeType.STAGE_ROLLBACK
                || request.changeType() == MajorChangeType.TERMINATE ? project.currentStageCode() : null;
        var amount = request.changeType() == MajorChangeType.TOTAL_AMOUNT ? project.totalAmount() : null;
        if (stage == null && amount == null) return;
        var submittedMonth = monthOf(request.submittedAt());
        var effectiveMonth = submittedMonth.isBefore(snapshots.activationMonth())
                ? snapshots.activationMonth() : submittedMonth;
        var reason = request.changeReason() == null
                ? "阶段推进审批通过：" + request.fromStageCode() + "→" + request.targetStageCode()
                : request.changeReason();
        var change = new Event(project.id(), effectiveMonth, Instant.now(clock), "APPROVAL", request.id(),
                reason, false, null, null, null, stage, amount, null);
        snapshots.insertEvent(change);
        reviseExisting(project.id(), effectiveMonth, change);
    }

    /** 每日检查缺失月份；正常情况下在每月 1 日 01:00 生成上月快照。 */
    public void generateDue() {
        var latestDue = currentMonth().minusMonths(1);
        while (true) {
            var generated = monthTransactions.execute(status -> {
                snapshots.lockControl();
                var month = snapshots.lastCompletedMonth().map(value -> value.plusMonths(1))
                        .orElseGet(snapshots::activationMonth);
                if (month.isAfter(latestDue)) return false;
                generate(month);
                snapshots.complete(month);
                return true;
            });
            if (!Boolean.TRUE.equals(generated)) return;
        }
    }

    private void generate(YearMonth month) {
        var at = Instant.now(clock);
        var pending = new ArrayList<PendingSnapshot>();
        for (var projectId : snapshots.projectIdsThrough(month)) {
            snapshots.lockProject(projectId);
            if (snapshots.find(projectId, month).isEmpty()) {
                pending.add(new PendingSnapshot(projectId, stateAt(projectId, month)));
            }
        }
        var cityNames = organizations.findCityNames(pending.stream().map(item -> item.state().cityId)
                .distinct().toList());
        Map<String, String> industryNames = industries.findAllById(pending.stream()
                .map(item -> item.state().industryCode).distinct().toList()).stream()
                .collect(Collectors.toMap(item -> item.industryCode(), item -> item.industryName()));
        for (var item : pending) {
            var state = item.state();
            snapshots.insert(new Snapshot(item.projectId(), month, state.provinceId, state.cityId,
                    cityNames.getOrDefault(state.cityId, state.cityId.toString()), state.industryCode,
                    industryNames.getOrDefault(state.industryCode, state.industryCode),
                    state.stageCode, state.totalAmount, state.categories, 0), at);
        }
    }

    private void reviseExisting(UUID projectId, YearMonth effectiveMonth, Event change) {
        var at = Instant.now(clock);
        for (var month : snapshots.existingMonths(projectId, effectiveMonth)) {
            var before = snapshots.find(projectId, month).orElseThrow();
            var after = toSnapshot(projectId, stateAt(projectId, month), month, before);
            if (!sameValues(before, after)) {
                snapshots.revise(before, after, change, at);
            }
        }
    }

    private MutableState stateAt(UUID projectId, YearMonth month) {
        MutableState state = null;
        for (var event : snapshots.eventsThrough(projectId, month)) {
            if (event.baseline()) {
                state = new MutableState(event.provinceId(), event.cityId(), event.industryCode(),
                        event.stageCode(), event.totalAmount(), event.categories());
            } else if (state != null) {
                if (event.industryCode() != null) state.industryCode = event.industryCode();
                if (event.stageCode() != null) state.stageCode = event.stageCode();
                if (event.totalAmount() != null) state.totalAmount = event.totalAmount();
                if (event.categories() != null) state.categories = event.categories();
            }
        }
        if (state == null) throw new IllegalStateException("项目缺少月末快照起始状态：" + projectId);
        return state;
    }

    private Snapshot toSnapshot(UUID projectId, MutableState state, YearMonth month, Snapshot previous) {
        var cityName = previous != null && previous.cityId().equals(state.cityId)
                ? previous.cityName() : organizations.findCityNames(List.of(state.cityId))
                        .getOrDefault(state.cityId, state.cityId.toString());
        var industryName = previous != null && previous.industryCode().equals(state.industryCode)
                ? previous.industryName() : industries.findById(state.industryCode)
                        .map(item -> item.industryName()).orElse(state.industryCode);
        return new Snapshot(projectId, month, state.provinceId, state.cityId, cityName,
                state.industryCode, industryName, state.stageCode, state.totalAmount, state.categories,
                previous == null ? 0 : previous.revisionNo());
    }

    private boolean sameValues(Snapshot first, Snapshot second) {
        return first.provinceId().equals(second.provinceId()) && first.cityId().equals(second.cityId())
                && first.industryCode().equals(second.industryCode())
                && first.stageCode().equals(second.stageCode())
                && first.totalAmount().compareTo(second.totalAmount()) == 0
                && first.categories().network().compareTo(second.categories().network()) == 0
                && first.categories().gewu().compareTo(second.categories().gewu()) == 0
                && first.categories().bigData().compareTo(second.categories().bigData()) == 0
                && first.categories().wangwu().compareTo(second.categories().wangwu()) == 0;
    }

    @Transactional(readOnly = true)
    public ProjectMonthlyWorkbench workbench(UUID actorId, YearMonth month, boolean includeTerminated) {
        var scope = readableScope(actorId);
        requireCompleted(month);
        var stageGroups = snapshots.group(month, scope, includeTerminated, "stage");
        var cityGroups = snapshots.group(month, scope, includeTerminated, "city");
        var industryGroups = snapshots.group(month, scope, includeTerminated, "industry");
        var categoryTotals = snapshots.categoryTotals(month, scope, includeTerminated);
        var byStage = stageGroups.stream().collect(Collectors.toMap(item -> item.code(), item -> item));
        var stages = Arrays.stream(ProjectStage.values())
                .filter(stage -> includeTerminated || stage != ProjectStage.TERMINATED)
                .map(stage -> {
                    var item = byStage.get(stage.name());
                    return new ProjectMonthlyWorkbench.StageTotal(stage.name(),
                            item == null ? 0 : item.projectCount(),
                            item == null ? BigDecimal.ZERO : item.amount());
                }).toList();
        var cities = cityGroups.stream().map(item -> new ProjectMonthlyWorkbench.CityTotal(
                UUID.fromString(item.code()), item.name(), item.projectCount(), item.amount()))
                .sorted(Comparator.comparing(ProjectMonthlyWorkbench.CityTotal::expectedAmount).reversed())
                .toList();
        var byIndustry = industryGroups.stream().map(item -> new ProjectMonthlyWorkbench.IndustryTotal(
                item.code(), item.name(), item.projectCount(), item.amount()))
                .sorted(Comparator.comparing(ProjectMonthlyWorkbench.IndustryTotal::expectedAmount).reversed())
                .toList();
        var count = stages.stream().mapToLong(ProjectMonthlyWorkbench.StageTotal::projectCount).sum();
        var amount = stages.stream().map(ProjectMonthlyWorkbench.StageTotal::expectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProjectMonthlyWorkbench(month, includeTerminated, count, amount, categoryTotals,
                stages, cities, byIndustry);
    }

    @Transactional(readOnly = true)
    public List<ProjectMonthlyTrendPoint> trend(UUID actorId, YearMonth from, YearMonth to,
            boolean includeTerminated) {
        var scope = readableScope(actorId);
        if (from == null || to == null || from.isAfter(to) || from.plusMonths(23).isBefore(to)) {
            throw new BusinessException(ProjectErrorCode.INVALID_SNAPSHOT_MONTH);
        }
        requireCompleted(from);
        requireCompleted(to);
        var result = new ArrayList<ProjectMonthlyTrendPoint>();
        for (var month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            var groups = snapshots.group(month, scope, includeTerminated, "stage");
            var count = groups.stream().mapToLong(item -> item.projectCount()).sum();
            var amount = groups.stream().map(item -> item.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new ProjectMonthlyTrendPoint(month, count, amount));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProjectMonthlyRevision> revisions(UUID actorId, UUID projectId, YearMonth month) {
        access.requireReadable(actorId, projectId);
        requireCompleted(month);
        return snapshots.revisions(projectId, month);
    }

    private ProjectDataScope readableScope(UUID actorId) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return identities.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    private void requireCompleted(YearMonth month) {
        if (month == null || month.isBefore(snapshots.activationMonth())
                || snapshots.lastCompletedMonth().filter(last -> !month.isAfter(last)).isEmpty()) {
            throw new BusinessException(ProjectErrorCode.SNAPSHOT_NOT_READY);
        }
    }

    private YearMonth currentMonth() { return monthOf(Instant.now(clock)); }
    private YearMonth monthOf(Instant at) { return YearMonth.from(at.atZone(BUSINESS_ZONE)); }

    private static final class MutableState {
        private final UUID provinceId;
        private final UUID cityId;
        private String industryCode;
        private String stageCode;
        private BigDecimal totalAmount;
        private ProjectCategoryTotals categories;

        private MutableState(UUID provinceId, UUID cityId, String industryCode,
                String stageCode, BigDecimal totalAmount, ProjectCategoryTotals categories) {
            this.provinceId = provinceId;
            this.cityId = cityId;
            this.industryCode = industryCode;
            this.stageCode = stageCode;
            this.totalAmount = totalAmount;
            this.categories = categories;
        }
    }

    private record PendingSnapshot(UUID projectId, MutableState state) { }
}
