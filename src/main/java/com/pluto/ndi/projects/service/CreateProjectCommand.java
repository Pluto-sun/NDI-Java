package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectNarrative;

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
        String nextAction,
        UUID draftId,
        boolean confirmNew,
        String duplicateReason,
        String signingSubjectCode,
        List<String> productSupportExpertNames,
        String plannedBidMonth,
        List<String> applicationScenarioCodes,
        ProjectNarrative narrative
) {

    public CreateProjectCommand {
        categoryAmounts = List.copyOf(categoryAmounts);
    }

    public CreateProjectCommand(UUID provinceId, UUID cityId, String projectName,
            String customerName, String industryCode, String customerNeed, BigDecimal totalAmount,
            UUID provinceLeaderUserId, String cityAccountManagerName, List<CategoryAmount> categoryAmounts,
            String initialProgress, String nextAction, UUID draftId, boolean confirmNew,
            String duplicateReason, String signingSubjectCode, List<String> productSupportExpertNames,
            String plannedBidMonth, List<String> applicationScenarioCodes) {
        this(provinceId, cityId, projectName, customerName, industryCode, customerNeed, totalAmount,
                provinceLeaderUserId, cityAccountManagerName, categoryAmounts, initialProgress, nextAction,
                draftId, confirmNew, duplicateReason, signingSubjectCode, productSupportExpertNames,
                plannedBidMonth, applicationScenarioCodes, null);
    }

    public CreateProjectCommand(UUID provinceId, UUID cityId, String projectName,
            String customerName, String industryCode, String customerNeed, BigDecimal totalAmount,
            UUID provinceLeaderUserId, String cityAccountManagerName, List<CategoryAmount> categoryAmounts,
            String initialProgress, String nextAction, UUID draftId, boolean confirmNew,
            String duplicateReason, String signingSubjectCode, List<String> productSupportExpertNames) {
        this(provinceId, cityId, projectName, customerName, industryCode, customerNeed, totalAmount,
                provinceLeaderUserId, cityAccountManagerName, categoryAmounts, initialProgress, nextAction,
                draftId, confirmNew, duplicateReason, signingSubjectCode, productSupportExpertNames, null, List.of());
    }

    public CreateProjectCommand(UUID provinceId, UUID cityId, String projectName,
            String customerName, String industryCode, String customerNeed, BigDecimal totalAmount,
            UUID provinceLeaderUserId, String cityAccountManagerName, List<CategoryAmount> categoryAmounts,
            String initialProgress, String nextAction, UUID draftId, boolean confirmNew,
            String duplicateReason, String signingSubjectCode) {
        this(provinceId, cityId, projectName, customerName, industryCode, customerNeed, totalAmount,
                provinceLeaderUserId, cityAccountManagerName, categoryAmounts, initialProgress, nextAction,
                draftId, confirmNew, duplicateReason, signingSubjectCode, List.of());
    }

    public CreateProjectCommand(UUID provinceId, UUID cityId, String projectName,
            String customerName, String industryCode, String customerNeed, BigDecimal totalAmount,
            UUID provinceLeaderUserId, String cityAccountManagerName, List<CategoryAmount> categoryAmounts,
            String initialProgress, String nextAction) {
        this(provinceId, cityId, projectName, customerName, industryCode, customerNeed, totalAmount,
                provinceLeaderUserId, cityAccountManagerName, categoryAmounts, initialProgress, nextAction,
                null, false, null, null);
    }

    public CreateProjectCommand(UUID provinceId, UUID cityId, String projectName,
            String customerName, String industryCode, String customerNeed, BigDecimal totalAmount,
            UUID provinceLeaderUserId, String cityAccountManagerName, List<CategoryAmount> categoryAmounts,
            String initialProgress, String nextAction, UUID draftId, boolean confirmNew,
            String duplicateReason) {
        this(provinceId, cityId, projectName, customerName, industryCode, customerNeed, totalAmount,
                provinceLeaderUserId, cityAccountManagerName, categoryAmounts, initialProgress, nextAction,
                draftId, confirmNew, duplicateReason, null);
    }

    /** 已选择三化类别的预计金额。 */
    public record CategoryAmount(String categoryCode, BigDecimal expectedAmount) {
    }
}
