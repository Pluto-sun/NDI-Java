package com.pluto.ndi.organization.service;

import java.util.Map;
import java.util.UUID;
import com.pluto.ndi.organization.domain.OrganizationErrorCode;
import com.pluto.ndi.organization.domain.entity.CityEntity;
import com.pluto.ndi.organization.repository.CityRepository;
import com.pluto.ndi.organization.repository.ProvinceRepository;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 管理员入口完成鉴权后调用，组织模块不反向依赖账号模块。 */
@Service
public class OrganizationAdministrationService {
    private final ProvinceRepository provinces;
    private final CityRepository cities;
    private final AuditWriter audit;
    public OrganizationAdministrationService(ProvinceRepository provinces, CityRepository cities, AuditWriter audit) {
        this.provinces = provinces; this.cities = cities; this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CityOption createCity(UUID actorId, UUID provinceId, String name) {
        String normalized = name == null ? "" : name.strip();
        if (provinceId == null || normalized.isBlank() || normalized.length() > 50) {
            throw new BusinessException(OrganizationErrorCode.CITY_INVALID);
        }
        // 锁定父省分，使同省并发建市按顺序检查重名，不重写既有组织数据。
        provinces.lockById(provinceId).filter(province -> province.enabled())
                .orElseThrow(() -> new BusinessException(OrganizationErrorCode.CITY_INVALID));
        if (cities.existsByProvinceIdAndCityName(provinceId, normalized)) {
            throw new BusinessException(OrganizationErrorCode.CITY_CONFLICT);
        }
        var city = cities.saveAndFlush(CityEntity.create(provinceId, normalized));
        audit.record(actorId, "ORGANIZATION_CITY", city.id(), "ORGANIZATION_CITY_CREATED",
                Map.of("provinceId", provinceId, "name", normalized));
        return new CityOption(city.id(), provinceId, normalized);
    }
}
