package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectProgressRepository extends JpaRepository<ProjectProgressEntity, UUID> {

    Page<ProjectProgressEntity> findByProjectIdOrderByOccurredAtDescIdDesc(UUID projectId, Pageable pageable);
}
