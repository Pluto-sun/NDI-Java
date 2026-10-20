package com.pluto.ndi.projects.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectExportStatus;
import com.pluto.ndi.projects.domain.entity.ProjectExportTaskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProjectExportTaskRepository extends JpaRepository<ProjectExportTaskEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectExportTaskEntity> findFirstByStatusOrderByRequestedAtAsc(ProjectExportStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectExportTaskEntity> findFirstByStatusAndStartedAtBeforeOrderByStartedAtAsc(
            ProjectExportStatus status, Instant before);
}
