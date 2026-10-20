package com.pluto.ndi.projects.repository;

import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectExportDownloadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectExportDownloadRepository extends JpaRepository<ProjectExportDownloadEntity, UUID> {
}
