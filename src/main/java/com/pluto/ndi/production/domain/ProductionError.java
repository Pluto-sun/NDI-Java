package com.pluto.ndi.production.domain;

import com.pluto.ndi.shared.web.ErrorCode;

import org.springframework.http.HttpStatus;

public record ProductionError(HttpStatus status, String code, String message) implements ErrorCode {
    public String title() {
        return message;
    }

    public static ProductionError invalid(String message) {
        return new ProductionError(HttpStatus.BAD_REQUEST, "PRODUCTION_INVALID", message);
    }

    public static ProductionError forbidden() {
        return new ProductionError(HttpStatus.FORBIDDEN, "PRODUCTION_FORBIDDEN", "无权访问或维护该排产记录");
    }

    public static ProductionError conflict() {
        return new ProductionError(
                HttpStatus.CONFLICT, "PRODUCTION_CONFLICT", "记录或任务已变化，请刷新后重试；同月份同省市项目及客户不可重复");
    }

    public static ProductionError notFound() {
        return new ProductionError(HttpStatus.NOT_FOUND, "PRODUCTION_NOT_FOUND", "排产记录不存在");
    }
}
