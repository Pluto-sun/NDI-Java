package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.projects.domain.entity.ProjectProgressEntity;

/** 保留原记录与更正链的进展展示数据。 */
public record ProjectProgressSummary(
        UUID id,
        UUID projectId,
        String progressContent,
        String nextAction,
        UUID actorId,
        Instant occurredAt,
        UUID correctsProgressId
) {
    public static ProjectProgressSummary from(ProjectProgressEntity progress) {
        return new ProjectProgressSummary(
                progress.id(), progress.projectId(), progress.progressContent(), progress.nextAction(),
                progress.actorId(), progress.occurredAt(), progress.correctsProgressId()
        );
    }
}
