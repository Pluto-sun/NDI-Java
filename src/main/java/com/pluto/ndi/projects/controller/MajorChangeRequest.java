package com.pluto.ndi.projects.controller;

import java.math.BigDecimal;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 重大变更申请接口输入。 */
public record MajorChangeRequest(
        @NotNull MajorChangeType changeType,
        @NotNull ProjectInitiatorLevel initiatorLevel,
        @NotNull UUID approverId,
        @NotBlank @Size(max = 2000) String reason,
        @Size(max = 200) String projectName,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal totalAmount,
        ProjectStage targetStage
) {
    MajorChangeCommand toCommand() {
        return new MajorChangeCommand(changeType, initiatorLevel, approverId, reason, projectName, totalAmount, targetStage);
    }
}
