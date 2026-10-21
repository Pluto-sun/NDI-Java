package com.pluto.ndi.organization.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import com.pluto.ndi.organization.domain.entity.CityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<CityEntity, UUID> {

    boolean existsByProvinceIdAndCityName(UUID provinceId, String cityName);

    Optional<CityEntity> findByIdAndProvinceIdAndEnabledTrue(UUID id, UUID provinceId);

    Optional<CityEntity> findByProvinceIdAndCityNameAndEnabledTrue(UUID provinceId, String cityName);

    List<CityEntity> findByEnabledTrueOrderByCityNameAsc();
}
