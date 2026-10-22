package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.organization.service.ProvinceOption;
import com.pluto.ndi.organization.service.RegionAdcodeCatalog;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectCategoryTotals;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectMapMetric;
import com.pluto.ndi.projects.repository.ProjectMapMetricRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 地图只返回聚合指标；省市授权必须先于聚合结果映射。 */
@Service
public class ProjectMapService {

    private final IdentityDirectory identities;
    private final OrganizationDirectory organizations;
    private final RegionAdcodeCatalog adcodes;
    private final ProjectMapMetricRepository metrics;
    private final ProjectMonthlySnapshotService monthlySnapshots;

    public ProjectMapService(IdentityDirectory identities, OrganizationDirectory organizations,
            RegionAdcodeCatalog adcodes, ProjectMapMetricRepository metrics,
            ProjectMonthlySnapshotService monthlySnapshots) {
        this.identities = identities;
        this.organizations = organizations;
        this.adcodes = adcodes;
        this.metrics = metrics;
        this.monthlySnapshots = monthlySnapshots;
    }

    @Transactional(readOnly = true)
    public ProjectMapView current(UUID actorId, UUID requestedProvinceId,
            boolean includeTerminated, ProjectStage stage) {
        return view(actorId, requestedProvinceId, null, includeTerminated, stage);
    }

    @Transactional(readOnly = true)
    public ProjectMapView view(UUID actorId, UUID requestedProvinceId, YearMonth month,
            boolean includeTerminated, ProjectStage stage) {
        return viewStages(actorId, requestedProvinceId, month, includeTerminated,
                stage == null ? List.of() : List.of(stage));
    }

    @Transactional(readOnly = true)
    public ProjectMapView viewStages(UUID actorId, UUID requestedProvinceId, YearMonth month,
            boolean includeTerminated, List<ProjectStage> stages) {
        var selected = stages == null ? List.<ProjectStage>of() : stages;
        if (selected.size() > ProjectStage.values().length || selected.stream().anyMatch(java.util.Objects::isNull)
                || selected.stream().distinct().count() != selected.size()) {
            throw new BusinessException(ProjectErrorCode.INVALID_SNAPSHOT_FILTER);
        }
        var scope = scope(actorId);
        var provinceId = switch (scope.level()) {
            case NATIONAL -> requestedProvinceId;
            case PROVINCE -> {
                if (requestedProvinceId != null && !scope.provinceIds().contains(requestedProvinceId)) {
                    throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
                }
                yield requestedProvinceId != null ? requestedProvinceId : scope.organizationId();
            }
            case CITY -> requireOwnProvince(cityProvinceId(scope.organizationId()), requestedProvinceId);
        };
        if (month != null) monthlySnapshots.requireCompleted(month);
        if (selected.contains(ProjectStage.TERMINATED) && !includeTerminated) {
            throw new BusinessException(ProjectErrorCode.INVALID_SNAPSHOT_FILTER);
        }
        var stageCodes = selected.stream().map(Enum::name).sorted().toList();
        var stageCode = stageCodes.size() == 1 ? stageCodes.getFirst() : null;
        if (provinceId == null) {
            var byProvince = metrics.summarizeStages(month, null, includeTerminated, stageCodes, scope).stream()
                    .collect(Collectors.toMap(ProjectMapMetric::regionId, Function.identity()));
            var regions = organizations.findEnabledProvinces().stream()
                    .filter(province -> scope.level() == ProjectDataScope.Level.NATIONAL || scope.provinceIds().contains(province.id()))
                    .map(province -> {
                        var item = byProvince.get(province.id());
                        return new ProjectMapView.Region(province.id(), province.name(),
                                adcodes.province(province.name()),
                                item == null ? 0 : item.projectCount(),
                                item == null ? BigDecimal.ZERO : item.expectedAmount(),
                                item == null ? ProjectCategoryTotals.zero() : item.categoryTotals(), true);
                    }).sorted(Comparator.comparing(ProjectMapView.Region::name)).toList();
            return new ProjectMapView("NATIONAL", "100000", null,
                    month, includeTerminated, stageCode, regions, stageCodes);
        }
        var province = organizations.findEnabledProvinces().stream()
                .filter(item -> item.id().equals(provinceId)).findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_ORGANIZATION));
        Map<UUID, ProjectMapMetric> byCity = metrics.summarizeStages(
                month, provinceId, includeTerminated, stageCodes, scope).stream()
                .collect(Collectors.toMap(ProjectMapMetric::regionId, Function.identity()));
        var regions = organizations.findEnabledCities().stream()
                .filter(city -> city.provinceId().equals(provinceId))
                .map(city -> cityRegion(scope, province, city, byCity.get(city.id())))
                .sorted(Comparator.comparing(ProjectMapView.Region::name)).toList();
        return new ProjectMapView("PROVINCE", adcodes.province(province.name()),
                new ProjectMapView.Province(province.id(), province.name()),
                month, includeTerminated, stageCode, regions, stageCodes);
    }

    @Transactional(readOnly = true)
    public void requireBoundaryAccess(UUID actorId, String adcode) {
        if (adcode == null || !adcode.matches("[0-9]{6}")) {
            throw new BusinessException(ProjectErrorCode.INVALID_MAP_ADCODE);
        }
        var scope = scope(actorId);
        if ("100000".equals(adcode) && (scope.level() == ProjectDataScope.Level.NATIONAL || scope.provinceIds().size() > 1)) return;
        var matchingProvinces = organizations.findEnabledProvinces().stream()
                .filter(item -> adcode.equals(adcodes.province(item.name()))).map(ProvinceOption::id)
                .collect(Collectors.toSet());
        if (matchingProvinces.isEmpty()) throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        if (scope.level() == ProjectDataScope.Level.NATIONAL
                || scope.level() == ProjectDataScope.Level.PROVINCE
                    && scope.provinceIds().stream().anyMatch(matchingProvinces::contains)
                || scope.level() == ProjectDataScope.Level.CITY
                    && matchingProvinces.contains(cityProvinceId(scope.organizationId()))) return;
        throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
    }

    private ProjectMapView.Region cityRegion(ProjectDataScope scope, ProvinceOption province,
            CityOption city, ProjectMapMetric metric) {
        var authorized = scope.level() != ProjectDataScope.Level.CITY
                || scope.organizationId().equals(city.id());
        return new ProjectMapView.Region(city.id(), city.name(),
                adcodes.city(province.name(), city.name()),
                !authorized || metric == null ? 0 : metric.projectCount(),
                !authorized || metric == null ? BigDecimal.ZERO : metric.expectedAmount(),
                !authorized || metric == null ? ProjectCategoryTotals.zero() : metric.categoryTotals(),
                authorized);
    }

    private UUID requireOwnProvince(UUID ownProvinceId, UUID requestedProvinceId) {
        if (requestedProvinceId != null && !requestedProvinceId.equals(ownProvinceId)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return ownProvinceId;
    }

    private UUID cityProvinceId(UUID cityId) {
        return organizations.findEnabledCities().stream()
                .filter(city -> city.id().equals(cityId)).map(CityOption::provinceId).findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    private ProjectDataScope scope(UUID actorId) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return identities.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }
}
