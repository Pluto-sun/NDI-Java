package com.pluto.ndi.organization.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pluto.ndi.organization.domain.entity.CityEntity;
import com.pluto.ndi.organization.repository.CityRepository;
import com.pluto.ndi.organization.repository.ProvinceRepository;
import org.springframework.stereotype.Service;

@Service
class OrganizationService implements OrganizationDirectory {

    private static final Set<String> MUNICIPALITY_CODES = Set.of("110000", "120000", "310000", "500000");

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
                .map(this::location);
    }

    @Override
    public Optional<OrganizationLocation> findEnabledLocationByNames(String provinceName, String cityName) {
        if (provinceName == null || provinceName.isBlank() || cityName == null || cityName.isBlank()) {
            return Optional.empty();
        }
        var shortProvince = stripAdministrativeSuffix(provinceName);
        var provinces = findEnabledProvinces().stream()
                .filter(province -> stripAdministrativeSuffix(province.name()).equals(shortProvince))
                .toList();
        if (provinces.size() != 1) return Optional.empty();
        return matchCity(provinces.getFirst().id(), cityName).map(this::location);
    }

    @Override
    public Optional<String> findEnabledCityName(UUID provinceId, String name) {
        return matchCity(provinceId, name).map(CityEntity::cityName);
    }

    private Optional<CityEntity> matchCity(UUID provinceId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        var input = name.strip();
        var cities = cityRepository.findByProvinceIdAndEnabledTrueOrderByCityNameAsc(provinceId);
        var exact = cities.stream().filter(item -> item.cityName().equals(input)).toList();
        if (!exact.isEmpty()) return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
        var shortName = stripAdministrativeSuffix(input);
        var matching = cities.stream().filter(item -> stripAdministrativeSuffix(item.cityName()).equals(shortName)
                || displayCityName(item).equals(input)).toList();
        return matching.size() == 1 ? Optional.of(matching.getFirst()) : Optional.empty();
    }

    @Override
    public Map<UUID, String> findCityNames(Collection<UUID> cityIds) {
        if (cityIds.isEmpty()) return Map.of();
        return cityRepository.findAllById(cityIds).stream()
                .collect(Collectors.toMap(CityEntity::id, this::displayCityName));
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
                .map(item -> new ProvinceOption(item.id(), item.provinceName(), MUNICIPALITY_CODES.contains(item.provinceCode())))
                .toList();
    }

    @Override
    public List<CityOption> findEnabledCities() {
        return cityRepository.findByEnabledTrueOrderByCityNameAsc().stream()
                .map(item -> new CityOption(item.id(), item.provinceId(), displayCityName(item),
                        MUNICIPALITY_CODES.contains(item.cityCode())))
                .toList();
    }

    @Override
    public Optional<ProvinceOption> findEnabledProvinceByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        var input = name.strip();
        var provinces = findEnabledProvinces();
        var exact = provinces.stream().filter(province -> province.name().equals(input)).toList();
        if (!exact.isEmpty()) return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
        var shortName = stripAdministrativeSuffix(input);
        var matching = provinces.stream().filter(province -> stripAdministrativeSuffix(province.name())
                .equals(shortName)).toList();
        return matching.size() == 1 ? Optional.of(matching.getFirst()) : Optional.empty();
    }

    private String displayCityName(CityEntity city) {
        var code = city.cityCode();
        if (code.length() != 6 || MUNICIPALITY_CODES.contains(code)
                || !MUNICIPALITY_CODES.contains(code.substring(0, 2) + "0000")) {
            return city.cityName();
        }
        return city.cityName().replaceFirst("(土家族苗族自治县|苗族土家族自治县|新区|区|县)$", "");
    }

    private OrganizationLocation location(CityEntity city) {
        return new OrganizationLocation(city.provinceId(), city.id(), MUNICIPALITY_CODES.contains(city.cityCode()));
    }

    private String stripAdministrativeSuffix(String name) {
        if (name == null) return "";
        name = name.strip();
        for (var suffix : new String[] {"特别行政区", "维吾尔自治区", "壮族自治区", "回族自治区", "自治区", "自治州", "地区", "省", "市", "盟", "区", "县", "旗"}) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }
}
