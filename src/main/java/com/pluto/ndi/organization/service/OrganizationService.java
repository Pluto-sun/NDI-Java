package com.pluto.ndi.organization.service;

import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.organization.domain.OrganizationLocation;
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
}
