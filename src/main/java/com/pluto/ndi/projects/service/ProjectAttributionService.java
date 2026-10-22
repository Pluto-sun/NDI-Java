package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectCategoryTotals;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectAttributionFilter;
import com.pluto.ndi.projects.repository.ProjectAttributionMetricRepository;
import com.pluto.ndi.projects.repository.ProjectAttributionMetricRepository.Metric;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 先限定可读范围，再汇总当前主档；归属移动后旧月份自然不再计数。 */
@Service
public class ProjectAttributionService {
    private final IdentityDirectory identities;
    private final OrganizationDirectory organizations;
    private final ProjectAttributionMetricRepository repository;

    public ProjectAttributionService(IdentityDirectory identities, OrganizationDirectory organizations,
            ProjectAttributionMetricRepository repository) {
        this.identities = identities;
        this.organizations = organizations;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProjectAttributionWorkbench view(UUID actorId, YearMonth from, YearMonth to,
            boolean includeTerminated, List<ProjectStage> stages, List<UUID> provinces, List<UUID> cities) {
        var filter = new ProjectAttributionFilter(from, to, includeTerminated, stages, provinces, cities);
        var selected = filter.stages();
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var scope = identities.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var metrics = repository.summarize(scope, filter);
        var effectiveStages = selected.isEmpty() ? Arrays.stream(ProjectStage.values())
                .filter(stage -> includeTerminated || stage != ProjectStage.TERMINATED).toList() : selected;
        var byMonthStage = metrics.stream().collect(Collectors.groupingBy(item -> item.month() + ":" + item.stageCode()));
        var trend = new ArrayList<ProjectStageTrendPoint>();
        for (var month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            for (var stage : effectiveStages) {
                var group = byMonthStage.getOrDefault(month + ":" + stage.name(), List.of());
                trend.add(new ProjectStageTrendPoint(month, stage.name(), count(group), amount(group), totals(group)));
            }
        }
        var provinceNames = organizations.findEnabledProvinces().stream()
                .collect(Collectors.toMap(item -> item.id(), item -> item.name()));
        var cityNames = organizations.findCityNames(metrics.stream().map(Metric::cityId).distinct().toList());
        return new ProjectAttributionWorkbench(from, to, includeTerminated,
                count(metrics), amount(metrics), totals(metrics), trend,
                regions(metrics, Metric::provinceId, provinceNames), regions(metrics, Metric::cityId, cityNames));
    }

    private List<ProjectAttributionWorkbench.Region> regions(List<Metric> metrics, Function<Metric, UUID> key,
            Map<UUID, String> names) {
        return metrics.stream().collect(Collectors.groupingBy(key)).entrySet().stream()
                .map(entry -> new ProjectAttributionWorkbench.Region(entry.getKey(),
                        names.getOrDefault(entry.getKey(), entry.getKey().toString()), count(entry.getValue()),
                        amount(entry.getValue()), totals(entry.getValue())))
                .sorted(Comparator.comparing(ProjectAttributionWorkbench.Region::expectedAmount).reversed()
                        .thenComparing(item -> item.id().toString())).toList();
    }

    private long count(List<Metric> rows) { return rows.stream().mapToLong(Metric::projectCount).sum(); }
    private BigDecimal amount(List<Metric> rows) {
        return rows.stream().map(Metric::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private ProjectCategoryTotals totals(List<Metric> rows) {
        var network = BigDecimal.ZERO; var gewu = BigDecimal.ZERO;
        var bigData = BigDecimal.ZERO; var wangwu = BigDecimal.ZERO;
        for (var row : rows) {
            network = network.add(row.categoryTotals().network());
            gewu = gewu.add(row.categoryTotals().gewu());
            bigData = bigData.add(row.categoryTotals().bigData());
            wangwu = wangwu.add(row.categoryTotals().wangwu());
        }
        return new ProjectCategoryTotals(network, gewu, bigData, wangwu);
    }
}
