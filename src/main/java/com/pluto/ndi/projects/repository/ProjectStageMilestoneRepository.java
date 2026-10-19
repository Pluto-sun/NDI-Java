package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectStageMilestoneRepository extends JpaRepository<ProjectStageMilestoneEntity, UUID> {
}
