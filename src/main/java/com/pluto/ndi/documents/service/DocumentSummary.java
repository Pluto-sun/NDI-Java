package com.pluto.ndi.documents.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.documents.domain.DocumentStatus;
import com.pluto.ndi.documents.domain.entity.DocumentEntity;

/** 业务模块可见的文档元数据，不暴露对象键和存储凭据。 */
public record DocumentSummary(
        UUID id,
        UUID scopeId,
        String purposeCode,
        String originalName,
        String mediaType,
        long byteSize,
        String sha256Hex,
        DocumentStatus status,
        Instant completedAt
) {
    static DocumentSummary from(DocumentEntity document) {
        return new DocumentSummary(
                document.id(), document.scopeId(), document.purposeCode(), document.originalName(),
                document.mediaType(), document.byteSize(), document.sha256Hex(),
                document.status(), document.completedAt()
        );
    }
}
