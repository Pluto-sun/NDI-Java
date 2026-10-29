package com.pluto.ndi.projects.repository;

import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectImportRowRepository extends JpaRepository<ProjectImportRowEntity, UUID> {
    List<ProjectImportRowEntity> findByBatchIdOrderByRowNumber(UUID batchId);

    /** 任务等待游标锁后可能已切换至新批次，当前读避免复用事务开始时的旧快照。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select item from ProjectImportRowEntity item where item.batchId = :batchId order by"
                + " item.rowNumber")
    List<ProjectImportRowEntity> findLockedByBatchId(UUID batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectImportRowEntity> findByBatchIdAndRowNumber(UUID batchId, int rowNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ProjectImportRowEntity item where item.id = :id")
    Optional<ProjectImportRowEntity> lockById(@Param("id") UUID id);
}
