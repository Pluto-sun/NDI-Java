package com.pluto.ndi.projects.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 创建生效项目所需的业务输入。 */
public record CreateProjectCommand(
        UUID provinceId,
        UUID cityId,
        String projectName,
        String customerName,
        String industryCode,
        String customerNeed,
        BigDecimal totalAmount,
        UUID provinceLeaderUserId,
        String cityAccountManagerName,
        List<CategoryAmount> categoryAmounts,
        String initialProgress,
        String nextAction
) {

    public CreateProjectCommand {
        categoryAmounts = List.copyOf(categoryAmounts);
    }

    /** 已选择三化类别的预计金额。 */
    public record CategoryAmount(String categoryCode, BigDecimal expectedAmount) {
    }
}
