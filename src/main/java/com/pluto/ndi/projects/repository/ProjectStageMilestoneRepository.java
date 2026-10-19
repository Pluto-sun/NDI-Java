package com.pluto.ndi.projects.repository;

import java.util.UUID;
import java.util.Optional;

import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectStageMilestoneRepository extends JpaRepository<ProjectStageMilestoneEntity, UUID> {
    Optional<ProjectStageMilestoneEntity> findFirstByProjectIdOrderByOccurredAtDesc(UUID projectId);

    Page<ProjectStageMilestoneEntity> findByProjectIdOrderByOccurredAtDescIdDesc(UUID projectId, Pageable pageable);
}
