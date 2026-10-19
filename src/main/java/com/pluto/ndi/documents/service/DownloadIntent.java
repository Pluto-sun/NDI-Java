package com.pluto.ndi.documents.service;

import java.time.Instant;

/** 限时读取凭证。 */
public record DownloadIntent(String downloadUrl, Instant expiresAt) {
}
