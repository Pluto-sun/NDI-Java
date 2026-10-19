package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectChangeEventRepository extends JpaRepository<ProjectChangeEventEntity, UUID> {
}
