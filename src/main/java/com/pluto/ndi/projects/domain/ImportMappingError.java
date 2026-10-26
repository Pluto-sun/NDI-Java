package com.pluto.ndi.projects.domain;

import com.pluto.ndi.shared.web.ErrorCode;

import org.springframework.http.HttpStatus;

import java.util.List;

/** 仅返回系统字段名，便于用户在映射步骤修正。 */
public record ImportMappingError(List<String> fields) implements ErrorCode {
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }

    public String code() {
        return "PROJECT_IMPORT_MAPPING_REQUIRED";
    }

    public String title() {
        return "必填字段缺少映射";
    }

    public String message() {
        return "请先映射必填字段：" + String.join("、", fields);
    }
}
