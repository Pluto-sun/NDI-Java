package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 未完成的新建项目表单，独立于正式项目主档。 */
@Entity
@Table(name = "project_draft")
public class ProjectDraftEntity {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectDraftEntity() { }

    public static ProjectDraftEntity create(UUID ownerId, String title, String payload, Instant at) {
        var entity = new ProjectDraftEntity();
        entity.id = UUID.randomUUID();
        entity.ownerId = ownerId;
        entity.update(title, payload, at);
        entity.createdAt = at;
        return entity;
    }

    public void update(String title, String payload, Instant at) {
        this.title = title;
        this.payload = payload;
        this.updatedAt = at;
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public String title() { return title; }
    public String payload() { return payload; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
