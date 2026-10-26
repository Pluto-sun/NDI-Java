package com.pluto.ndi.organization.domain;

import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrganizationErrorCode implements ErrorCode {
    CATALOG_READ_ONLY(HttpStatus.CONFLICT,"省市采用预置行政区划目录，不允许自行新增"),
    PROVINCE_INVALID(HttpStatus.BAD_REQUEST, "省分名称不能为空且不得超过50个字符"),
    PROVINCE_CONFLICT(HttpStatus.CONFLICT, "已存在同名省分"),
    CITY_INVALID(HttpStatus.BAD_REQUEST, "省分无效或地市名称不符合要求"),
    CITY_CONFLICT(HttpStatus.CONFLICT, "该省分下已存在同名地市");
    private final HttpStatus status;
    private final String message;
    OrganizationErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public HttpStatus status() { return status; }
    public String code() { return "ORGANIZATION_" + name(); }
    public String title() { return message; }
    public String message() { return message; }
}
