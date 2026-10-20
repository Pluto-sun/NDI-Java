package com.pluto.ndi.organization.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;


/** 向业务模块提供组织归属校验，不暴露组织持久化模型。 */
public interface OrganizationDirectory {

    Optional<OrganizationLocation> findEnabledLocation(UUID provinceId, UUID cityId);

    Optional<OrganizationLocation> findEnabledLocationByNames(String provinceName, String cityName);

    Map<UUID, String> findCityNames(Collection<UUID> cityIds);

    List<ProvinceOption> findEnabledProvinces();

    List<CityOption> findEnabledCities();
}
