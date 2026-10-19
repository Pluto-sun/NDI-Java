package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ProjectStage;

/** 正向阶段推进申请输入。 */
public record StageAdvanceCommand(
        ProjectInitiatorLevel initiatorLevel,
        ProjectStage targetStage,
        UUID approverId,
        LocalDate stageOccurredOn,
        UUID evidenceDocumentId,
        String contractNo,
        BigDecimal signedAmount,
        String progressContent,
        String nextAction
) {
}
