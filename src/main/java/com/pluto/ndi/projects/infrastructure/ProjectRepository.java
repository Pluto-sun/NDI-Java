package com.pluto.ndi.projects.infrastructure;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Page<ProjectEntity> findByProvinceId(UUID provinceId, Pageable pageable);

    Page<ProjectEntity> findByCityId(UUID cityId, Pageable pageable);
}
