package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectStageMilestoneEntity;

/** 项目阶段历史的只读展示数据。 */
public record ProjectMilestoneSummary(
        UUID id,
        UUID projectId,
        String stageCode,
        String sourceType,
        UUID actorId,
        Instant occurredAt,
        String descriptionText
) {
    public static ProjectMilestoneSummary from(ProjectStageMilestoneEntity milestone) {
        return new ProjectMilestoneSummary(
                milestone.id(), milestone.projectId(), milestone.stageCode(), milestone.sourceType(),
                milestone.actorId(), milestone.occurredAt(), milestone.descriptionText()
        );
    }
}
