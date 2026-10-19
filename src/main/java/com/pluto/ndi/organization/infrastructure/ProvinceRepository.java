package com.pluto.ndi.organization.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProvinceRepository extends JpaRepository<ProvinceEntity, UUID> {

    boolean existsByIdAndEnabledTrue(UUID id);
}
