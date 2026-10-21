package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
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
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectCityAggregate;
import com.pluto.ndi.projects.repository.ProjectProvinceAggregate;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 地图只返回聚合指标；省市授权必须先于聚合结果映射。 */
@Service
public class ProjectMapService {

    private final IdentityDirectory identities;
    private final OrganizationDirectory organizations;
    private final RegionAdcodeCatalog adcodes;
    private final ProjectRepository projects;

    public ProjectMapService(IdentityDirectory identities, OrganizationDirectory organizations,
            RegionAdcodeCatalog adcodes, ProjectRepository projects) {
        this.identities = identities;
        this.organizations = organizations;
        this.adcodes = adcodes;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public ProjectMapView current(UUID actorId, UUID requestedProvinceId,
            boolean includeTerminated, ProjectStage stage) {
        var scope = scope(actorId);
        var provinceId = switch (scope.level()) {
            case NATIONAL -> requestedProvinceId;
            case PROVINCE -> requireOwnProvince(scope.organizationId(), requestedProvinceId);
            case CITY -> requireOwnProvince(cityProvinceId(scope.organizationId()), requestedProvinceId);
        };
        var stageCode = stage == null ? null : stage.name();
        if (provinceId == null) {
            var metrics = projects.summarizeMapProvinces(includeTerminated, stageCode).stream()
                    .collect(Collectors.toMap(ProjectProvinceAggregate::getProvinceId, Function.identity()));
            var regions = organizations.findEnabledProvinces().stream()
                    .map(province -> {
                        var item = metrics.get(province.id());
                        return new ProjectMapView.Region(province.id(), province.name(),
                                adcodes.province(province.name()),
                                item == null ? 0 : item.getProjectCount(),
                                item == null ? BigDecimal.ZERO : item.getExpectedAmount(), true);
                    }).sorted(Comparator.comparing(ProjectMapView.Region::name)).toList();
            return new ProjectMapView("NATIONAL", "100000", null,
                    includeTerminated, stageCode, regions);
        }
        var province = organizations.findEnabledProvinces().stream()
                .filter(item -> item.id().equals(provinceId)).findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_ORGANIZATION));
        Map<UUID, ProjectCityAggregate> metrics = projects.summarizeMapCities(
                provinceId, includeTerminated, stageCode).stream()
                .collect(Collectors.toMap(ProjectCityAggregate::getCityId, Function.identity()));
        var regions = organizations.findEnabledCities().stream()
                .filter(city -> city.provinceId().equals(provinceId))
                .map(city -> cityRegion(scope, province, city, metrics.get(city.id())))
                .sorted(Comparator.comparing(ProjectMapView.Region::name)).toList();
        return new ProjectMapView("PROVINCE", adcodes.province(province.name()),
                new ProjectMapView.Province(province.id(), province.name()),
                includeTerminated, stageCode, regions);
    }

    @Transactional(readOnly = true)
    public void requireBoundaryAccess(UUID actorId, String adcode) {
        if (adcode == null || !adcode.matches("[0-9]{6}")) {
            throw new BusinessException(ProjectErrorCode.INVALID_MAP_ADCODE);
        }
        var scope = scope(actorId);
        if ("100000".equals(adcode) && scope.level() == ProjectDataScope.Level.NATIONAL) return;
        var province = organizations.findEnabledProvinces().stream()
                .filter(item -> adcode.equals(adcodes.province(item.name()))).findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        if (scope.level() == ProjectDataScope.Level.NATIONAL
                || scope.level() == ProjectDataScope.Level.PROVINCE
                    && scope.organizationId().equals(province.id())
                || scope.level() == ProjectDataScope.Level.CITY
                    && cityProvinceId(scope.organizationId()).equals(province.id())) return;
        throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
    }

    private ProjectMapView.Region cityRegion(ProjectDataScope scope, ProvinceOption province,
            CityOption city, ProjectCityAggregate metric) {
        var authorized = scope.level() != ProjectDataScope.Level.CITY
                || scope.organizationId().equals(city.id());
        return new ProjectMapView.Region(city.id(), city.name(),
                adcodes.city(province.name(), city.name()),
                !authorized || metric == null ? 0 : metric.getProjectCount(),
                !authorized || metric == null ? BigDecimal.ZERO : metric.getExpectedAmount(), authorized);
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
