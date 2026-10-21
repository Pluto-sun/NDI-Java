package com.pluto.ndi.shared.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 会话模式用于正式认证；开发身份模式仅允许在本地环境启用。 */
@Validated
@ConfigurationProperties("ndi.auth")
public record AuthenticationProperties(
        @DefaultValue("SESSION") Mode mode,
        @DefaultValue("8") @Min(1) @Max(168) int sessionTtlHours,
        @DefaultValue("true") boolean secureCookie
) {
    public enum Mode { SESSION, LOCAL_ACTOR }
}
