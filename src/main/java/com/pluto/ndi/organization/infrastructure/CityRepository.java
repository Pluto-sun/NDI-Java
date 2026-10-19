package com.pluto.ndi.organization.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CityRepository extends JpaRepository<CityEntity, UUID> {

    Optional<CityEntity> findByIdAndProvinceIdAndEnabledTrue(UUID id, UUID provinceId);
}
