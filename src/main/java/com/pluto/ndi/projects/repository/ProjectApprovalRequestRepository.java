package com.pluto.ndi.projects.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<ProjectApprovalRequestEntity> findByApproverIdAndStatusOrderByDueAtAsc(
            UUID approverId, ApprovalStatus status, Pageable pageable);

    @Query("""
            select request from ProjectApprovalRequestEntity request
            where request.status = :status and request.dueAt <= :now
              and (request.dueAt > :cursorDueAt
                   or (request.dueAt = :cursorDueAt and request.id > :cursorId))
            order by request.dueAt asc, request.id asc
            """)
    List<ProjectApprovalRequestEntity> findOverdueAfter(
            ApprovalStatus status, Instant now, Instant cursorDueAt, UUID cursorId, Pageable pageable);
}
