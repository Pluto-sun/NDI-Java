package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectDashboardFilter;
import com.pluto.ndi.projects.domain.ProjectDashboardActivity;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectDashboardRepository;
import com.pluto.ndi.projects.repository.ProjectDashboardRepository.Bucket;
import com.pluto.ndi.projects.repository.ProjectDashboardActivityRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectDashboardService {
    private final IdentityDirectory identities;
    private final OrganizationDirectory organizations;
    private final ProjectDashboardRepository repository;
    private final ProjectDashboardActivityRepository activities;
    public ProjectDashboardService(IdentityDirectory identities, OrganizationDirectory organizations,
            ProjectDashboardRepository repository, ProjectDashboardActivityRepository activities) {
        this.identities = identities; this.organizations = organizations; this.repository = repository;
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    public List<ProjectDashboardActivity> activities(UUID actorId, ProjectDashboardFilter filter) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var scope = identities.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return activities.recent(scope, filter);
    }

    @Transactional(readOnly = true)
    public ProjectDashboard view(UUID actorId, ProjectDashboardFilter filter) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var scope = identities.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var rows = repository.summarize(scope, filter);
        long length = ChronoUnit.MONTHS.between(filter.from(), filter.to()) + 1;
        var previous = filter.from().minusMonths(length).getYear() < 1000 ? List.<Bucket>of()
                : repository.summarize(scope, new ProjectDashboardFilter(filter.from().minusMonths(length),
                        filter.from().minusMonths(1), filter.stage(), filter.category(), filter.product(), filter.includeTerminated()));
        var stages = Arrays.stream(ProjectStage.values()).filter(stage -> filter.includeTerminated()
                || stage != ProjectStage.TERMINATED).map(stage -> new ProjectDashboard.Stage(stage.name(),
                        summarize(rows.stream().filter(row -> row.stage().equals(stage.name())).toList()))).toList();
        var names = organizations.findProvinceNames(rows.stream().map(Bucket::provinceId).distinct().toList());
        var provinces = rows.stream().collect(Collectors.groupingBy(Bucket::provinceId)).entrySet().stream()
                .map(entry -> new ProjectDashboard.Region(entry.getKey(), names.getOrDefault(entry.getKey(), "未知省分"),
                        summarize(entry.getValue())))
                .sorted(Comparator.comparing((ProjectDashboard.Region region) -> region.summary().totalAmount())
                        .reversed().thenComparing(region -> region.id().toString())).toList();
        var trend = new ArrayList<ProjectDashboard.Month>();
        for (var month = filter.from(); !month.isAfter(filter.to()); month = month.plusMonths(1)) {
            var selected = month;
            trend.add(new ProjectDashboard.Month(month,
                    summarize(rows.stream().filter(row -> row.month().equals(selected)).toList())));
        }
        return new ProjectDashboard(filter.from(), filter.to(), summarize(rows), summarize(previous), stages, provinces, trend);
    }

    static ProjectDashboard.Summary summarize(List<Bucket> rows) {
        return new ProjectDashboard.Summary(count(rows, Bucket::count), amount(rows, Bucket::amount),
                rows.stream().map(Bucket::provinceId).distinct().count(),
                value(rows, Bucket::networkCount, Bucket::networkAmount),
                value(rows, Bucket::digitalCount, row -> row.gewuAmount().add(row.bigDataAmount())),
                value(rows, Bucket::wangwuCount, Bucket::wangwuAmount),
                value(rows, Bucket::gewuCount, Bucket::gewuAmount),
                value(rows, Bucket::bigDataCount, Bucket::bigDataAmount));
    }
    private static long count(List<Bucket> rows, ToLongFunction<Bucket> field) { return rows.stream().mapToLong(field).sum(); }
    private static BigDecimal amount(List<Bucket> rows, Function<Bucket, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static ProjectDashboard.Value value(List<Bucket> rows, ToLongFunction<Bucket> count,
            Function<Bucket, BigDecimal> amount) {
        return new ProjectDashboard.Value(count(rows, count), amount(rows, amount));
    }
}
