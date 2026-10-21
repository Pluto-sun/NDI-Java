package com.pluto.ndi.projects.controller;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import com.pluto.ndi.projects.service.MajorChangeItem;
import com.pluto.ndi.projects.domain.ProjectChangeField;
import jakarta.validation.Valid;
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
        ProjectStage targetStage,
        @Size(max = 7) List<@NotNull @Valid FieldInput> items
) {
    public record FieldInput(@NotNull ProjectChangeField fieldCode, @NotBlank @Size(max = 200) String newValue) { }

    MajorChangeCommand toCommand() {
        return new MajorChangeCommand(changeType, initiatorLevel, approverId, reason, projectName, totalAmount, targetStage,
                items == null ? List.of() : items.stream().map(item -> new MajorChangeItem(item.fieldCode(), item.newValue())).toList());
    }
}
