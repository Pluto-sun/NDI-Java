package com.pluto.ndi.projects.domain.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 导入行人工修订记录，保存修订前后的模板字段与校验结果。 */
@Entity
@Table(name = "project_import_row_revision")
public class ProjectImportRowRevisionEntity {
    @Id private UUID id;
    @Column(name = "row_id", nullable = false) private UUID rowId;
    @Column(name = "revision_no", nullable = false) private int revisionNo;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "before_payload", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String beforePayload;
    @Column(name = "after_payload", nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String afterPayload;
    @Column(name = "result_status", nullable = false, length = 24) private String resultStatus;
    @Column(nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON) private String errors;
    @Column(name = "revised_at", nullable = false) private Instant revisedAt;

    protected ProjectImportRowRevisionEntity() { }

    public static ProjectImportRowRevisionEntity create(ProjectImportRowEntity row, UUID actorId,
            String before, Instant at) {
        var item = new ProjectImportRowRevisionEntity();
        item.id = UUID.randomUUID();
        item.rowId = row.id();
        item.revisionNo = row.revisionNo();
        item.actorId = actorId;
        item.beforePayload = before;
        item.afterPayload = row.sourcePayload();
        item.resultStatus = row.status();
        item.errors = row.errors();
        item.revisedAt = at;
        return item;
    }
}
