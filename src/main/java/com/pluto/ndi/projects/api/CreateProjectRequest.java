package com.pluto.ndi.projects.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.application.CreateProjectCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建项目接口的输入。 */
public record CreateProjectRequest(
        @NotNull(message = "归属省分不得为空") UUID provinceId,
        @NotNull(message = "归属地市不得为空") UUID cityId,
        @NotBlank(message = "项目名称不得为空") @Size(max = 200, message = "项目名称不得超过200个字符") String projectName,
        @NotBlank(message = "客户名称不得为空") @Size(max = 200, message = "客户名称不得超过200个字符") String customerName,
        @NotBlank(message = "客户所属行业不得为空") @Size(max = 3, message = "客户所属行业代码不正确") String industryCode,
        @NotBlank(message = "客户需求不得为空") String customerNeed,
        @NotNull(message = "项目总金额不得为空") @DecimalMin(value = "0", message = "项目总金额不得小于0") BigDecimal totalAmount,
        @NotNull(message = "省分负责人不得为空") UUID provinceLeaderUserId,
        @NotBlank(message = "地市客户经理不得为空") @Size(max = 100, message = "地市客户经理不得超过100个字符") String cityAccountManagerName,
        @NotEmpty(message = "至少选择一个三化类别") List<@Valid CategoryAmountRequest> categoryAmounts,
        @NotBlank(message = "首条项目进展不得为空") String initialProgress,
        @NotBlank(message = "下一步动作不得为空") String nextAction
) {

    CreateProjectCommand toCommand() {
        return new CreateProjectCommand(
                provinceId,
                cityId,
                projectName,
                customerName,
                industryCode,
                customerNeed,
                totalAmount,
                provinceLeaderUserId,
                cityAccountManagerName,
                categoryAmounts.stream()
                        .map(item -> new CreateProjectCommand.CategoryAmount(item.categoryCode(), item.expectedAmount()))
                        .toList(),
                initialProgress,
                nextAction
        );
    }

    /** 已选三化类别及其预计金额。 */
    public record CategoryAmountRequest(
            @NotBlank(message = "三化类别不得为空") String categoryCode,
            @NotNull(message = "三化类别金额不得为空")
            @DecimalMin(value = "0", message = "三化类别金额不得小于0") BigDecimal expectedAmount
    ) {
    }
}
