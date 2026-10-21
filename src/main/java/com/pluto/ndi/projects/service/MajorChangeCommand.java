package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectStage;

/** 重大变更申请输入，未涉及的目标字段保持为空。 */
public record MajorChangeCommand(
        MajorChangeType changeType,
        ProjectInitiatorLevel initiatorLevel,
        UUID approverId,
        String reason,
        String projectName,
        BigDecimal totalAmount,
        ProjectStage targetStage,
        List<MajorChangeItem> items
) {
    public MajorChangeCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public MajorChangeCommand(MajorChangeType changeType, ProjectInitiatorLevel initiatorLevel, UUID approverId,
            String reason, String projectName, BigDecimal totalAmount, ProjectStage targetStage) {
        this(changeType, initiatorLevel, approverId, reason, projectName, totalAmount, targetStage, List.of());
    }
}
