package com.pluto.ndi.projects.controller;

import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.service.UpdateProjectProfileCommand;
import com.pluto.ndi.projects.domain.ProjectNarrative;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 普通信息使用全量替换，避免把未提交字段误解释为清空。 */
public record UpdateProjectProfileRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 200) String customerName,
        @NotBlank @Size(max = 3) String industryCode,
        @NotBlank @Size(max = 10_000) String customerNeed,
        UUID provinceLeaderUserId,
        @Size(max = 100) String cityAccountManagerName,
        List<String> productSupportExpertNames,
        String plannedBidMonth,
        List<String> applicationScenarioCodes,
        String constructionContentRaw,
        String productPromotionContent,
        String remark
) {
    public UpdateProjectProfileCommand toCommand() {
        var narrative = constructionContentRaw == null && productPromotionContent == null && remark == null
                ? null : new ProjectNarrative(constructionContentRaw, productPromotionContent, remark);
        return new UpdateProjectProfileCommand(expectedVersion, customerName, industryCode, customerNeed,
                provinceLeaderUserId, cityAccountManagerName, productSupportExpertNames,
                plannedBidMonth, applicationScenarioCodes, narrative);
    }
}
