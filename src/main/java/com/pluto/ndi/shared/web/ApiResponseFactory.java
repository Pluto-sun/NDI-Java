package com.pluto.ndi.shared.web;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "成功";

    private final Clock clock;

    public ApiResponseFactory(Clock clock) {
        this.clock = clock;
    }

    public <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                data,
                RequestCorrelationFilter.currentRequestId().orElseGet(() -> UUID.randomUUID().toString()),
                Instant.now(clock)
        );
    }
}
