package com.pluto.ndi.projects.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectImportRowRepository extends JpaRepository<ProjectImportRowEntity, UUID> {
    List<ProjectImportRowEntity> findByBatchIdOrderByRowNumber(UUID batchId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectImportRowEntity> findByBatchIdAndRowNumber(UUID batchId, int rowNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ProjectImportRowEntity item where item.id = :id")
    Optional<ProjectImportRowEntity> lockById(@Param("id") UUID id);
}
