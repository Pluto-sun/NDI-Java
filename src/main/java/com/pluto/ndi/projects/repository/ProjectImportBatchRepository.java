package com.pluto.ndi.projects.repository;

import com.pluto.ndi.projects.domain.entity.ProjectImportBatchEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectImportBatchRepository
        extends JpaRepository<ProjectImportBatchEntity, UUID> {
    Page<ProjectImportBatchEntity> findByActorIdOrderByCreatedAtDescIdDesc(
            UUID actorId, Pageable pageable);

    Page<ProjectImportBatchEntity> findByActorIdAndStatusInOrderByCreatedAtDescIdDesc(
            UUID actorId, java.util.Collection<String> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectImportBatchEntity> findByIdAndActorId(UUID id, UUID actorId);

    List<ProjectImportBatchEntity> findTop20ByStatusAndConfirmedAtBeforeOrderByConfirmedAtAsc(
            String status, Instant before);
}
