package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_approval_action")
public class ProjectApprovalActionEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "action_code", nullable = false)
    private String actionCode;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "action_at", nullable = false)
    private Instant actionAt;

    @Column(name = "opinion")
    private String opinion;

    protected ProjectApprovalActionEntity() {
    }

    public static ProjectApprovalActionEntity create(UUID requestId, String actionCode, UUID actorId, Instant at, String opinion) {
        var entity = new ProjectApprovalActionEntity();
        entity.id = UUID.randomUUID();
        entity.requestId = requestId;
        entity.actionCode = actionCode;
        entity.actorId = actorId;
        entity.actionAt = at;
        entity.opinion = opinion;
        return entity;
    }
}
