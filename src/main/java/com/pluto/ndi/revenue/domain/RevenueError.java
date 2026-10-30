package com.pluto.ndi.revenue.domain;

import com.pluto.ndi.shared.web.ErrorCode;

import org.springframework.http.HttpStatus;

public record RevenueError(HttpStatus status, String code, String message) implements ErrorCode {
    public String title() {
        return message;
    }

    public static RevenueError invalid(String message) {
        return new RevenueError(HttpStatus.BAD_REQUEST, "REVENUE_INVALID", message);
    }

    public static RevenueError forbidden() {
        return new RevenueError(HttpStatus.FORBIDDEN, "REVENUE_FORBIDDEN", "无权访问或维护该计收记录");
    }

    public static RevenueError conflict() {
        return new RevenueError(
                HttpStatus.CONFLICT, "REVENUE_CONFLICT", "记录或任务已变化，请刷新重试；同月合同及条款不能重复");
    }

    public static RevenueError notFound() {
        return new RevenueError(HttpStatus.NOT_FOUND, "REVENUE_NOT_FOUND", "计收记录不存在");
    }
}
