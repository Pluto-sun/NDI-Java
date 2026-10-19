package com.pluto.ndi.projects.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 审批决定接口输入；驳回意见由业务服务校验为必填。 */
public record ApprovalDecisionRequest(
        @NotNull Boolean approved,
        @Size(max = 2000) String opinion
) {
}
