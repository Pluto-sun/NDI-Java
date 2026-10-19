package com.pluto.ndi.documents.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 客户端直接上传至对象存储所需的一次性信息。 */
public record UploadIntent(
        UUID documentId,
        String method,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
    public UploadIntent {
        requiredHeaders = Map.copyOf(requiredHeaders);
    }
}
