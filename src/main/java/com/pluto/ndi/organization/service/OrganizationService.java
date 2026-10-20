package com.pluto.ndi.organization.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

import com.pluto.ndi.organization.domain.entity.CityEntity;
import com.pluto.ndi.organization.repository.CityRepository;
import com.pluto.ndi.organization.repository.ProvinceRepository;
import org.springframework.stereotype.Service;

@Service
class OrganizationService implements OrganizationDirectory {

    private final CityRepository cityRepository;
    private final ProvinceRepository provinceRepository;

    OrganizationService(CityRepository cityRepository, ProvinceRepository provinceRepository) {
        this.cityRepository = cityRepository;
        this.provinceRepository = provinceRepository;
    }

    @Override
    public Optional<OrganizationLocation> findEnabledLocation(UUID provinceId, UUID cityId) {
        if (!provinceRepository.existsByIdAndEnabledTrue(provinceId)) {
            return Optional.empty();
        }
        return cityRepository.findByIdAndProvinceIdAndEnabledTrue(cityId, provinceId)
                .map(ignored -> new OrganizationLocation(provinceId, cityId));
    }

    @Override
    public Optional<OrganizationLocation> findEnabledLocationByNames(String provinceName, String cityName) {
        var province = provinceRepository.findByProvinceNameAndEnabledTrue(provinceName)
                .or(() -> provinceRepository.findByProvinceNameAndEnabledTrue(stripAdministrativeSuffix(provinceName)));
        return province.flatMap(item -> cityRepository.findByProvinceIdAndCityNameAndEnabledTrue(
                        item.id(), cityName)
                .or(() -> cityRepository.findByProvinceIdAndCityNameAndEnabledTrue(
                        item.id(), stripAdministrativeSuffix(cityName)))
                .map(city -> new OrganizationLocation(item.id(), city.id())));
    }

    @Override
    public Map<UUID, String> findCityNames(Collection<UUID> cityIds) {
        if (cityIds.isEmpty()) return Map.of();
        return cityRepository.findAllById(cityIds).stream()
                .collect(Collectors.toMap(CityEntity::id, CityEntity::cityName));
    }

    private String stripAdministrativeSuffix(String name) {
        if (name == null) return "";
        for (var suffix : new String[] {"自治区", "自治州", "地区", "省", "市"}) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }
}
