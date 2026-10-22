package com.pluto.ndi.organization.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
        if (provinceName == null || provinceName.isBlank() || cityName == null || cityName.isBlank()) {
            return Optional.empty();
        }
        // 两侧统一行政后缀；存在重名时拒绝猜测，不跨省匹配地市。
        var provinces = provinceRepository.findByEnabledTrueOrderByProvinceNameAsc().stream()
                .filter(item -> stripAdministrativeSuffix(item.provinceName())
                        .equals(stripAdministrativeSuffix(provinceName))).toList();
        if (provinces.size() != 1) return Optional.empty();
        var provinceId = provinces.getFirst().id();
        var cities = cityRepository.findByProvinceIdAndEnabledTrueOrderByCityNameAsc(provinceId).stream()
                .filter(item -> stripAdministrativeSuffix(item.cityName())
                        .equals(stripAdministrativeSuffix(cityName))).toList();
        return cities.size() == 1 ? Optional.of(new OrganizationLocation(provinceId, cities.getFirst().id()))
                : Optional.empty();
    }

    @Override
    public Map<UUID, String> findCityNames(Collection<UUID> cityIds) {
        if (cityIds.isEmpty()) return Map.of();
        return cityRepository.findAllById(cityIds).stream()
                .collect(Collectors.toMap(CityEntity::id, CityEntity::cityName));
    }

    @Override
    public Map<UUID, String> findProvinceNames(Collection<UUID> provinceIds) {
        if (provinceIds.isEmpty()) return Map.of();
        return provinceRepository.findAllById(provinceIds).stream().collect(Collectors.toUnmodifiableMap(
                com.pluto.ndi.organization.domain.entity.ProvinceEntity::id,
                com.pluto.ndi.organization.domain.entity.ProvinceEntity::provinceName));
    }

    @Override
    public List<ProvinceOption> findEnabledProvinces() {
        return provinceRepository.findByEnabledTrueOrderByProvinceNameAsc().stream()
                .map(item -> new ProvinceOption(item.id(), item.provinceName()))
                .toList();
    }

    @Override
    public List<CityOption> findEnabledCities() {
        return cityRepository.findByEnabledTrueOrderByCityNameAsc().stream()
                .map(item -> new CityOption(item.id(), item.provinceId(), item.cityName()))
                .toList();
    }

    private String stripAdministrativeSuffix(String name) {
        if (name == null) return "";
        name = name.strip();
        for (var suffix : new String[] {"特别行政区", "维吾尔自治区", "壮族自治区", "回族自治区", "自治区", "自治州", "地区", "省", "市"}) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }
}
