package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectExportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_export_task")
public class ProjectExportTaskEntity {

    @Id
    private UUID id;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false)
    private ProjectDataScope.Level scopeLevel;

    @Column(name = "scope_organization_id", nullable = false)
    private UUID scopeOrganizationId;

    @Column(name = "filter_json", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private String filterJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectExportStatus status;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected ProjectExportTaskEntity() {
    }

    public static ProjectExportTaskEntity pending(UUID id, UUID actorId, ProjectDataScope scope,
                                                   String filterJson, Instant requestedAt) {
        var task = new ProjectExportTaskEntity();
        task.id = id;
        task.requestedBy = actorId;
        task.scopeLevel = scope.level();
        task.scopeOrganizationId = scope.organizationId();
        task.filterJson = filterJson;
        task.status = ProjectExportStatus.PENDING;
        task.requestedAt = requestedAt;
        return task;
    }

    public UUID id() { return id; }
    public UUID requestedBy() { return requestedBy; }
    public ProjectDataScope scope() { return new ProjectDataScope(scopeLevel, scopeOrganizationId); }
    public String filterJson() { return filterJson; }
    public ProjectExportStatus status() { return status; }
    public Integer rowCount() { return rowCount; }
    public Instant requestedAt() { return requestedAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public String errorMessage() { return errorMessage; }

    public void start(Instant now) {
        status = ProjectExportStatus.RUNNING;
        startedAt = now;
    }

    public void complete(int rows, Instant now) {
        status = ProjectExportStatus.COMPLETED;
        rowCount = rows;
        finishedAt = now;
        errorMessage = null;
    }

    public void fail(String message, Instant now) {
        status = ProjectExportStatus.FAILED;
        finishedAt = now;
        errorMessage = message;
    }
}
