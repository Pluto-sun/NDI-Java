package com.pluto.ndi.projects.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 驾驶舱的已生效变动，不向接口暴露事件原始载荷。 */
public record ProjectDashboardActivity(String id, UUID projectId, String projectName, String province,
        String industry, String products, String leaderName, String eventType, Instant occurredAt,
        String beforeStage, String afterStage, BigDecimal beforeAmount, BigDecimal afterAmount) {
    public ProjectDashboardActivity withResponsibleNames(String names) {
        return new ProjectDashboardActivity(id,projectId,projectName,province,industry,products,names,eventType,
            occurredAt,beforeStage,afterStage,beforeAmount,afterAmount);
    }
}
