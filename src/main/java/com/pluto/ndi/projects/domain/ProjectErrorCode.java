package com.pluto.ndi.projects.domain;

import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** 项目模块对外暴露的稳定错误码。 */
public enum ProjectErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在", "未找到指定项目"),
    ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "PROJECT_ACCESS_FORBIDDEN", "无权访问项目", "当前账号无权访问项目数据"),
    CREATION_FORBIDDEN(HttpStatus.FORBIDDEN, "PROJECT_CREATION_FORBIDDEN", "无权创建项目", "当前账号无权在目标属地创建项目"),
    INVALID_ORGANIZATION(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_ORGANIZATION", "项目属地无效", "省分与地市不存在、未启用或不匹配"),
    INVALID_PROVINCE_LEADER(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_PROVINCE_LEADER", "省分负责人无效", "省分负责人必须是本省已启用的省分负责人账号"),
    INVALID_INDUSTRY(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_INDUSTRY", "客户所属行业无效", "客户所属行业必须使用已启用的工业行业代码"),
    INVALID_CATEGORY_AMOUNT(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_CATEGORY_AMOUNT", "三化类别金额无效", "三化类别金额必须完整、非负且合计不得超过项目总金额");

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String message;

    ProjectErrorCode(HttpStatus status, String code, String title, String message) {
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
