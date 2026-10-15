package com.pluto.ndi.shared.security;

import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SecurityErrorCode implements ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", "身份认证失败", "请先完成身份认证"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "无权访问", "当前身份无权访问该资源");

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String message;

    SecurityErrorCode(HttpStatus status, String code, String title, String message) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String message() {
        return message;
    }
}
