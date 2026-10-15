package com.pluto.ndi.shared.web;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_ARGUMENT", "请求参数不合法", "请求参数不符合要求"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "COMMON_VALIDATION_FAILED", "请求校验失败", "请求参数校验未通过"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_MALFORMED_REQUEST", "请求内容不合法", "请求内容无法解析"),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMON_TYPE_MISMATCH", "请求参数类型不匹配", "请求参数类型不正确"),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_CONFLICT", "资源状态冲突", "当前资源状态不允许该操作"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_METHOD_NOT_ALLOWED", "请求方法不支持", "当前资源不支持该请求方法"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_UNSUPPORTED_MEDIA_TYPE", "请求媒体类型不支持", "请求媒体类型不受支持"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "系统内部错误", "系统暂时无法处理该请求");

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String message;

    CommonErrorCode(HttpStatus status, String code, String title, String message) {
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
