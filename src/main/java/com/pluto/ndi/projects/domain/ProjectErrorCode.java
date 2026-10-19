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
    INVALID_CATEGORY_AMOUNT(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_CATEGORY_AMOUNT", "三化类别金额无效", "三化类别金额必须完整、非负且合计不得超过项目总金额"),
    INVALID_MAJOR_CHANGE(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_MAJOR_CHANGE", "重大变更内容无效", "变更内容必须符合当前项目阶段和金额规则"),
    APPROVER_INVALID(HttpStatus.BAD_REQUEST, "PROJECT_APPROVER_INVALID", "审批人无效", "审批人必须具有目标省份的对应审批职责且不能是发起人"),
    APPROVAL_FORBIDDEN(HttpStatus.FORBIDDEN, "PROJECT_APPROVAL_FORBIDDEN", "无权处理审批", "仅申请指定的审批人或发起人有权执行对应操作"),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_APPROVAL_NOT_FOUND", "审批申请不存在", "未找到指定审批申请"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "PROJECT_NOTIFICATION_NOT_FOUND", "审批通知不存在", "未找到当前账号的指定通知"),
    APPROVAL_NOT_PENDING(HttpStatus.CONFLICT, "PROJECT_APPROVAL_NOT_PENDING", "审批申请已结束", "申请不再处于待审批状态"),
    PENDING_APPROVAL_EXISTS(HttpStatus.CONFLICT, "PROJECT_PENDING_APPROVAL_EXISTS", "已有待审批申请", "同一项目同一时间只能存在一条待审批申请"),
    APPROVAL_VERSION_CONFLICT(HttpStatus.CONFLICT, "PROJECT_APPROVAL_VERSION_CONFLICT", "项目版本冲突", "项目数据已变化，请刷新后重新发起申请"),
    INVALID_STAGE_ADVANCEMENT(HttpStatus.BAD_REQUEST, "PROJECT_INVALID_STAGE_ADVANCEMENT", "阶段推进内容无效", "只能申请相邻正向阶段，且阶段日期与字段必须完整"),
    STAGE_EVIDENCE_REQUIRED(HttpStatus.BAD_REQUEST, "PROJECT_STAGE_EVIDENCE_REQUIRED", "阶段材料无效", "必须提供属于本项目且已完成上传校验的指定材料"),
    CONTRACT_NO_CONFLICT(HttpStatus.CONFLICT, "PROJECT_CONTRACT_NO_CONFLICT", "合同编号重复", "合同编号必须在全系统唯一");

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
