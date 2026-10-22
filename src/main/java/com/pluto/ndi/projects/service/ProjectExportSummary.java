package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import com.pluto.ndi.projects.domain.ProjectExportStatus;
import com.pluto.ndi.projects.domain.entity.ProjectExportTaskEntity;

/** 导出任务的状态与审计摘要。 */
public record ProjectExportSummary(
        UUID id,
        ProjectExportStatus status,
        Integer rowCount,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        List<String> fields
) {
    public ProjectExportSummary {
        fields = List.copyOf(fields);
    }

    public static ProjectExportSummary from(ProjectExportTaskEntity task) {
        return new ProjectExportSummary(task.id(), task.status(), task.rowCount(),
                task.requestedAt(), task.startedAt(), task.finishedAt(), task.errorMessage(), task.selectedFields());
    }
}
