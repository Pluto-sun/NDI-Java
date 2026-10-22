package com.pluto.ndi.projects.service;

import java.util.List;
import java.util.Arrays;
import com.pluto.ndi.projects.domain.ProjectExportField;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ExportFileDirectory;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectExportStatus;
import com.pluto.ndi.projects.domain.entity.ProjectExportDownloadEntity;
import com.pluto.ndi.projects.domain.entity.ProjectExportTaskEntity;
import com.pluto.ndi.projects.repository.ProjectExportDownloadRepository;
import com.pluto.ndi.projects.repository.ProjectExportTaskRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目导出任务的授权、状态与操作审计。 */
@Service
public class ProjectExportService {

    public static final int MAX_ROWS = 1000;
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(10);

    private final ProjectService projectService;
    private final IdentityDirectory identityDirectory;
    private final ProjectExportTaskRepository taskRepository;
    private final ProjectExportDownloadRepository downloadRepository;
    private final ExportFileDirectory files;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProjectExportService(ProjectService projectService, IdentityDirectory identityDirectory,
                                ProjectExportTaskRepository taskRepository,
                                ProjectExportDownloadRepository downloadRepository,
                                ExportFileDirectory files, ObjectMapper objectMapper, Clock clock) {
        this.projectService = projectService;
        this.identityDirectory = identityDirectory;
        this.taskRepository = taskRepository;
        this.downloadRepository = downloadRepository;
        this.files = files;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ProjectExportSummary submit(UUID actorId, ProjectQuery query) {
        return submit(actorId, query, null);
    }

    @Transactional
    public ProjectExportSummary submit(UUID actorId, ProjectQuery query, List<String> fields) {
        var scope = currentScope(actorId);
        var selected = ProjectExportField.select(fields).stream().map(ProjectExportField::code).toList();
        var count = projectService.countProjects(actorId, query);
        if (count > MAX_ROWS) {
            throw new BusinessException(ProjectErrorCode.EXPORT_TOO_LARGE);
        }
        var task = ProjectExportTaskEntity.pending(UUID.randomUUID(), actorId, scope,
                serialize(query), selected, Instant.now(clock));
        return ProjectExportSummary.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<FieldOption> fields(UUID actorId) {
        currentScope(actorId);
        return Arrays.stream(ProjectExportField.values())
                .map(field -> new FieldOption(field.code(), field.label(), field.defaultSelected())).toList();
    }

    public record FieldOption(String code, String label, boolean defaultSelected) { }

    @Transactional(readOnly = true)
    public ProjectExportSummary find(UUID actorId, UUID exportId) {
        return ProjectExportSummary.from(requireOwned(actorId, exportId));
    }

    @Transactional
    public ProjectExportDownload download(UUID actorId, UUID exportId) {
        var task = requireOwned(actorId, exportId);
        if (task.status() != ProjectExportStatus.COMPLETED) {
            throw new BusinessException(ProjectErrorCode.EXPORT_NOT_READY);
        }
        var url = files.signCsvDownload(exportId);
        downloadRepository.save(ProjectExportDownloadEntity.create(exportId, actorId, Instant.now(clock)));
        return new ProjectExportDownload(url, "projects-" + exportId + ".csv");
    }

    @Transactional
    public Optional<ExportWork> claimPending() {
        return taskRepository.findFirstByStatusOrderByRequestedAtAsc(ProjectExportStatus.PENDING)
                .map(task -> {
                    try {
                        var query = deserialize(task.filterJson());
                        var fields = ProjectExportField.select(task.selectedFields());
                        task.start(Instant.now(clock));
                        return new ExportWork(task.id(), task.requestedBy(), task.scope(), query, fields);
                    } catch (RuntimeException exception) {
                        task.fail("导出任务条件无效，请重新提交", Instant.now(clock));
                        return null;
                    }
                });
    }

    @Transactional
    public void expireStale() {
        taskRepository.findFirstByStatusAndStartedAtBeforeOrderByStartedAtAsc(
                        ProjectExportStatus.RUNNING, Instant.now(clock).minus(RUN_TIMEOUT))
                .ifPresent(task -> task.fail("导出处理超时，请重新提交", Instant.now(clock)));
    }

    @Transactional
    public void complete(UUID exportId, int rowCount) {
        taskRepository.findById(exportId)
                .filter(task -> task.status() == ProjectExportStatus.RUNNING)
                .ifPresent(task -> task.complete(rowCount, Instant.now(clock)));
    }

    @Transactional
    public void fail(UUID exportId) {
        taskRepository.findById(exportId)
                .filter(task -> task.status() == ProjectExportStatus.RUNNING)
                .ifPresent(task -> task.fail("导出生成失败，请重新提交", Instant.now(clock)));
    }

    public void verifyScope(ExportWork work) {
        if (!work.scope().equals(currentScope(work.actorId()))) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
    }

    private ProjectExportTaskEntity requireOwned(UUID actorId, UUID exportId) {
        var task = taskRepository.findById(exportId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.EXPORT_NOT_FOUND));
        if (!task.requestedBy().equals(actorId) || !task.scope().equals(currentScope(actorId))) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return task;
    }

    private ProjectDataScope currentScope(UUID actorId) {
        return identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .flatMap(identityDirectory::findReadableProjectScope)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    private String serialize(ProjectQuery query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目导出条件无法序列化", exception);
        }
    }

    private ProjectQuery deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProjectQuery.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目导出条件无法解析", exception);
        }
    }

    /** 后台处理所需的最小任务快照。 */
    public record ExportWork(UUID id, UUID actorId, ProjectDataScope scope, ProjectQuery query,
                             List<ProjectExportField> fields) {
    }
}
