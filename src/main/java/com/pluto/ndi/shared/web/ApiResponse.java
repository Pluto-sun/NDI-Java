package com.pluto.ndi.shared.web;

import java.time.Instant;
import java.util.Objects;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp
) {

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
