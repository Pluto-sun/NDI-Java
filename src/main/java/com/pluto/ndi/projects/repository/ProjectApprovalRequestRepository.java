package com.pluto.ndi.projects.repository;

import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ProjectApprovalRequestRepository extends JpaRepository<ProjectApprovalRequestEntity, UUID> {
    boolean existsByProjectIdAndStatus(UUID projectId, ApprovalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ProjectApprovalRequestEntity request where request.id = :id")
    Optional<ProjectApprovalRequestEntity> findLockedById(UUID id);

    @Query("select request.projectId from ProjectApprovalRequestEntity request where request.id = :id")
    Optional<UUID> findProjectIdById(UUID id);
}
