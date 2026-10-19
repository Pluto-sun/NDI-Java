package com.pluto.ndi.documents.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 对象存储连接配置；生产凭据由环境变量提供。 */
@Validated
@ConfigurationProperties(prefix = "ndi.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String publicEndpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        boolean autoCreateBucket
) {
}
