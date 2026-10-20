package com.pluto.ndi.projects.repository;

import java.util.List;
import java.util.UUID;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectImportRowRepository extends JpaRepository<ProjectImportRowEntity, UUID> {
    List<ProjectImportRowEntity> findByBatchIdOrderByRowNumber(UUID batchId);
}
