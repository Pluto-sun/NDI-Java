package com.pluto.ndi.projects.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectApprovalNotificationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProjectApprovalNotificationRepository extends JpaRepository<ProjectApprovalNotificationEntity, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ProjectApprovalNotificationEntity> {

    Page<ProjectApprovalNotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProjectApprovalNotificationEntity> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query(value = """
            INSERT INTO project_approval_notification (
                id, request_id, recipient_id, event_key, kind, created_at
            ) VALUES (
                UUID_TO_BIN(:id), UUID_TO_BIN(:requestId), UUID_TO_BIN(:recipientId),
                :eventKey, 'OVERDUE', :createdAt
            ) ON DUPLICATE KEY UPDATE event_key = event_key
            """, nativeQuery = true)
    int insertOverdueIfAbsent(
            String id, String requestId, String recipientId, String eventKey, Instant createdAt
    );
}
