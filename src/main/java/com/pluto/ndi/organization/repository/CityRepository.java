package com.pluto.ndi.organization.repository;

import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.organization.domain.entity.CityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<CityEntity, UUID> {

    Optional<CityEntity> findByIdAndProvinceIdAndEnabledTrue(UUID id, UUID provinceId);
}
