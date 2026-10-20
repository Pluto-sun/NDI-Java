package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProjectChangeEventRepository extends JpaRepository<ProjectChangeEventEntity, UUID> {
    Page<ProjectChangeEventEntity> findByProjectIdOrderByOccurredAtDescIdDesc(UUID projectId, Pageable pageable);
}
