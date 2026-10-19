package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectApprovalActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectApprovalActionRepository extends JpaRepository<ProjectApprovalActionEntity, UUID> {
}
