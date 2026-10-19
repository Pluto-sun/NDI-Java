package com.pluto.ndi.documents.domain;

import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** 文档模块的稳定业务错误码。 */
public enum DocumentErrorCode implements ErrorCode {
    INVALID_UPLOAD(HttpStatus.BAD_REQUEST, "DOCUMENT_INVALID_UPLOAD", "文件信息无效", "文件名称、类型、大小或哈希不符合上传要求"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "文件不存在", "未找到指定文档"),
    ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "DOCUMENT_ACCESS_FORBIDDEN", "无权操作文件", "当前账号无权操作该文档"),
    NOT_AVAILABLE(HttpStatus.CONFLICT, "DOCUMENT_NOT_AVAILABLE", "文件尚不可用", "文件尚未完成上传校验"),
    INTEGRITY_FAILED(HttpStatus.CONFLICT, "DOCUMENT_INTEGRITY_FAILED", "文件校验失败", "对象内容与申请的大小、类型或 SHA-256 不一致"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DOCUMENT_STORAGE_UNAVAILABLE", "对象存储不可用", "请稍后重试文件操作");

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String message;

    DocumentErrorCode(HttpStatus status, String code, String title, String message) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String title() { return title; }
    public String message() { return message; }
}
