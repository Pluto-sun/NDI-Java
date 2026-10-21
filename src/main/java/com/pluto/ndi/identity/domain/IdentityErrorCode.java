package com.pluto.ndi.identity.domain;

import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

public enum IdentityErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号或密码错误"),
    SESSION_INVALID(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "当前密码不正确"),
    PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "新密码须为 6 至 128 个字符，且不能与当前密码相同"),
    PROFILE_INVALID(HttpStatus.BAD_REQUEST, "姓名或邮箱格式不正确"),
    EMAIL_CONFLICT(HttpStatus.CONFLICT, "该邮箱已被其他账号使用"),
    SESSION_MODE_REQUIRED(HttpStatus.NOT_FOUND, "当前环境未启用密码登录");

    private final HttpStatus status;
    private final String message;
    IdentityErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public HttpStatus status() { return status; }
    public String code() { return "IDENTITY_" + name(); }
    public String title() { return message; }
    public String message() { return message; }
}
