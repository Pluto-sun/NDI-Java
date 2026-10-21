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
    SESSION_MODE_REQUIRED(HttpStatus.NOT_FOUND, "当前环境未启用密码登录"),
    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "仅系统管理员有权维护账号与组织"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "账号不存在"),
    ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "账号或邮箱已被占用，请刷新后重试"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "账号已被其他操作修改，请刷新后重新编辑"),
    ACCOUNT_INVALID(HttpStatus.BAD_REQUEST, "新账号须为 11 位手机号，姓名及邮箱须符合格式要求"),
    ROLE_SCOPE_INVALID(HttpStatus.BAD_REQUEST, "角色组合或省市归属不正确，请检查角色与组织范围"),
    SELF_ADMIN_PROTECTED(HttpStatus.CONFLICT, "不能停用自己或移除自己的系统管理员角色"),
    LAST_ADMIN_PROTECTED(HttpStatus.CONFLICT, "不能移除最后一个可登录的系统管理员");

    private final HttpStatus status;
    private final String message;
    IdentityErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public HttpStatus status() { return status; }
    public String code() { return "IDENTITY_" + name(); }
    public String title() { return message; }
    public String message() { return message; }
}
