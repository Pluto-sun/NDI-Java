package com.pluto.ndi.organization.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.organization.OrganizationDirectory;
import com.pluto.ndi.organization.OrganizationLocation;
import org.springframework.stereotype.Service;

@Service
class JpaOrganizationDirectory implements OrganizationDirectory {

    private final CityRepository cityRepository;
    private final ProvinceRepository provinceRepository;

    JpaOrganizationDirectory(CityRepository cityRepository, ProvinceRepository provinceRepository) {
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
