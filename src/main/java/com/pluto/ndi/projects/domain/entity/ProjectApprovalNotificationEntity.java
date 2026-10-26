package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_approval_notification")
public class ProjectApprovalNotificationEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "event_key", nullable = false, length = 160)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApprovalNotificationKind kind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected ProjectApprovalNotificationEntity() {
    }

    public static ProjectApprovalNotificationEntity create(
            UUID requestId, UUID recipientId, ApprovalNotificationKind kind, Instant at
    ) {
        var entity = new ProjectApprovalNotificationEntity();
        entity.id = UUID.randomUUID();
        entity.requestId = requestId;
        entity.recipientId = recipientId;
        entity.eventKey = requestId + ":" + kind.name();
        entity.kind = kind;
        entity.createdAt = at;
        return entity;
    }

    public static ProjectApprovalNotificationEntity handover(UUID requestId, UUID recipientId, Instant at) {
        var entity = create(requestId, recipientId, ApprovalNotificationKind.REASSIGNED, at);
        entity.eventKey += ":" + UUID.randomUUID();
        return entity;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public UUID recipientId() { return recipientId; }
    public String eventKey() { return eventKey; }
    public ApprovalNotificationKind kind() { return kind; }
    public Instant createdAt() { return createdAt; }
    public Instant readAt() { return readAt; }
}
