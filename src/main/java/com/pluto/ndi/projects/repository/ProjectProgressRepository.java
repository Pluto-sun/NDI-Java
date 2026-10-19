package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectProgressRepository extends JpaRepository<ProjectProgressEntity, UUID> {
}
