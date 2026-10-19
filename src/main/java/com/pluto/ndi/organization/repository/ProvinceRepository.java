package com.pluto.ndi.organization.repository;

import java.util.UUID;

import com.pluto.ndi.organization.domain.entity.ProvinceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<ProvinceEntity, UUID> {

    boolean existsByIdAndEnabledTrue(UUID id);
}
