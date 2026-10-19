package com.pluto.ndi.projects.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.service.StageAdvanceCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 阶段推进申请接口输入。 */
public record StageAdvanceRequest(
        @NotNull(message = "发起职责不得为空") ProjectInitiatorLevel initiatorLevel,
        @NotNull(message = "目标阶段不得为空") ProjectStage targetStage,
        @NotNull(message = "审批人不得为空") UUID approverId,
        @NotNull(message = "阶段发生日期不得为空") LocalDate stageOccurredOn,
        UUID evidenceDocumentId,
        @Size(max = 100) String contractNo,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal signedAmount,
        String progressContent,
        String nextAction
) {
    StageAdvanceCommand toCommand() {
        return new StageAdvanceCommand(
                initiatorLevel, targetStage, approverId, stageOccurredOn, evidenceDocumentId,
                contractNo, signedAmount, progressContent, nextAction
        );
    }
}
