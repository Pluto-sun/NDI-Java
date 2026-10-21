package com.pluto.ndi.backup.domain;

import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BackupErrorCode implements ErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "BACKUP_FORBIDDEN", "无权管理备份", "仅已启用的系统管理员有权管理完整备份"),
    DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_DISABLED", "备份未启用", "请先配置受限备份目录并启用备份服务"),
    BUSY(HttpStatus.CONFLICT, "BACKUP_BUSY", "备份正在进行", "已有待处理或运行中的备份，请稍后刷新记录"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "BACKUP_NOT_FOUND", "备份不存在", "未找到指定备份记录"),
    NOT_READY(HttpStatus.CONFLICT, "BACKUP_NOT_READY", "备份不可下载", "仅成功生成的备份支持下载"),
    UNAVAILABLE(HttpStatus.GONE, "BACKUP_UNAVAILABLE", "备份文件不可用", "文件缺失或校验失败，请联系管理员核查存储，不要使用该归档恢复"),
    REQUEST_CONFLICT(HttpStatus.CONFLICT, "BACKUP_REQUEST_CONFLICT", "请求编号冲突", "该请求编号已被使用，请重新发起备份");
    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String message;
    BackupErrorCode(HttpStatus status, String code, String title, String message) {
        this.status = status; this.code = code; this.title = title; this.message = message;
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String title() { return title; }
    public String message() { return message; }
}
