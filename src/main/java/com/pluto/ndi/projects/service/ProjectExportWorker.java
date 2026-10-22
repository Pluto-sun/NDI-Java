package com.pluto.ndi.projects.service;

import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.projects.util.ProjectCsvWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 从持久化任务表领取并生成有界 CSV 文件。 */
@Component
public class ProjectExportWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectExportWorker.class);

    private final ProjectExportService exportService;
    private final ProjectService projectService;
    private final ExportFileDirectory files;
    private final ProjectExportRowService rows;

    public ProjectExportWorker(ProjectExportService exportService, ProjectService projectService,
                               ExportFileDirectory files, ProjectExportRowService rows) {
        this.exportService = exportService;
        this.projectService = projectService;
        this.files = files;
        this.rows = rows;
    }

    @Scheduled(fixedDelayString = "${ndi.projects.export.poll-delay-ms:5000}",
            initialDelayString = "${ndi.projects.export.initial-delay-ms:5000}")
    public void runPending() {
        exportService.expireStale();
        exportService.claimPending().ifPresent(work -> {
            try {
                exportService.verifyScope(work);
                var page = projectService.findProjects(work.actorId(), work.query(),
                        PageRequest.of(0, ProjectExportService.MAX_ROWS + 1,
                                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
                if (page.getTotalElements() > ProjectExportService.MAX_ROWS) {
                    throw new IllegalStateException("项目导出结果超过行数限制");
                }
                var content = ProjectCsvWriter.writeRows(rows.rows(page.getContent(), work.fields()), work.fields());
                exportService.verifyScope(work);
                files.storeCsv(work.id(), content);
                exportService.complete(work.id(), page.getNumberOfElements());
            } catch (RuntimeException exception) {
                LOGGER.error("项目导出任务失败，任务编号：{}", work.id(), exception);
                exportService.fail(work.id());
            }
        });
    }
}
