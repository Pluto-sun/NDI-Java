package com.pluto.ndi.documents.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.documents.domain.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_object")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "staging_object_key", nullable = false)
    private String stagingObjectKey;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "storage_etag")
    private String storageEtag;

    protected DocumentEntity() {
    }

    public static DocumentEntity pending(
            UUID id, String scopeType, UUID scopeId, String purposeCode, String bucketName,
            String stagingObjectKey, String objectKey, String originalName, String mediaType,
            long byteSize, String sha256Hex, UUID uploadedBy, Instant createdAt
    ) {
        var entity = new DocumentEntity();
        entity.id = id;
        entity.scopeType = scopeType;
        entity.scopeId = scopeId;
        entity.purposeCode = purposeCode;
        entity.bucketName = bucketName;
        entity.stagingObjectKey = stagingObjectKey;
        entity.objectKey = objectKey;
        entity.originalName = originalName;
        entity.mediaType = mediaType;
        entity.byteSize = byteSize;
        entity.sha256Hex = sha256Hex;
        entity.status = DocumentStatus.PENDING;
        entity.uploadedBy = uploadedBy;
        entity.createdAt = createdAt;
        return entity;
    }

    public void markAvailable(String etag, Instant completedAt) {
        if (status != DocumentStatus.PENDING) {
            throw new IllegalStateException("文档已完成上传校验");
        }
        status = DocumentStatus.AVAILABLE;
        storageEtag = etag;
        this.completedAt = completedAt;
    }

    public UUID id() { return id; }
    public String scopeType() { return scopeType; }
    public UUID scopeId() { return scopeId; }
    public String purposeCode() { return purposeCode; }
    public String bucketName() { return bucketName; }
    public String stagingObjectKey() { return stagingObjectKey; }
    public String objectKey() { return objectKey; }
    public String originalName() { return originalName; }
    public String mediaType() { return mediaType; }
    public long byteSize() { return byteSize; }
    public String sha256Hex() { return sha256Hex; }
    public DocumentStatus status() { return status; }
    public UUID uploadedBy() { return uploadedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
    public String storageEtag() { return storageEtag; }
}
