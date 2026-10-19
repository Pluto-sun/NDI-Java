package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Page<ProjectEntity> findByProvinceId(UUID provinceId, Pageable pageable);

    Page<ProjectEntity> findByCityId(UUID cityId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectEntity project where project.id = :id")
    Optional<ProjectEntity> findLockedById(UUID id);
}
