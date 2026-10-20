package com.pluto.ndi.projects.repository;

import java.util.UUID;
import com.pluto.ndi.projects.domain.entity.ProjectImportBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProjectImportBatchRepository extends JpaRepository<ProjectImportBatchEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectImportBatchEntity> findByIdAndActorId(UUID id, UUID actorId);
}
