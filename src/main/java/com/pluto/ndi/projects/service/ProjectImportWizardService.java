package com.pluto.ndi.projects.service;

import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.domain.ProjectImportTask.Configuration;
import com.pluto.ndi.projects.mapper.ProjectImportTaskMapper;
import com.pluto.ndi.projects.repository.ProjectImportBatchRepository;
import com.pluto.ndi.projects.util.ProjectExcelReader;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class ProjectImportWizardService {
    public record FieldRule(String name, String requirement, String hint) {}

    public record View(
            UUID id,
            long version,
            int step,
            String fileName,
            ProjectExcelReader.WorkbookInspection inspection,
            Configuration configuration,
            List<Integer> selectedRows,
            ImportBatchSummary batch,
            Instant updatedAt,
            List<FieldRule> fieldRules) {}

    private final ProjectImportTaskMapper tasks;
    private final ImportWorkbookDirectory files;
    private final ProjectImportService imports;
    private final IdentityDirectory identity;
    private final ProjectImportBatchRepository batches;
    private final ProjectImportTaskCleanup cleanup;
    private final Clock clock;
    private final ProjectExcelReader reader;

    public ProjectImportWizardService(
            ProjectImportTaskMapper tasks,
            ImportWorkbookDirectory files,
            ProjectImportService imports,
            IdentityDirectory identity,
            ProjectImportBatchRepository batches,
            ProjectImportTaskCleanup cleanup,
            Clock clock,
            ProjectExcelReader reader) {
        this.tasks = tasks;
        this.files = files;
        this.imports = imports;
        this.identity = identity;
        this.batches = batches;
        this.cleanup = cleanup;
        this.clock = clock;
        this.reader = reader;
    }

    private void authorize(UUID actor) {
        if (!identity.canUseProjectImport(actor))
            throw new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN);
    }

    @Transactional
    public View current(UUID actor) {
        authorize(actor);
        tasks.lock(actor);
        var task = tasks.findLocked(actor).orElse(null);
        if (task != null
                && task.batchId() != null
                && !Set.of("PREVIEWED", "CONFIRMING")
                        .contains(
                                batches.findByIdAndActorId(task.batchId(), actor)
                                        .orElseThrow(ProjectImportWizardService::conflict)
                                        .status())) {
            cleanup.completed(task.batchId());
            return null;
        }
        return task == null ? null : view(task);
    }

    @Transactional
    public View upload(
            UUID actor, UUID expectedTaskId, Long expectedVersion, String name, byte[] bytes) {
        authorize(actor);
        tasks.lock(actor);
        var old = tasks.findLocked(actor);
        if (old.isPresent()
                && (old.get().step() != 1
                        || !old.get().id().equals(expectedTaskId)
                        || expectedVersion == null
                        || old.get().version() != expectedVersion)) throw conflict();
        var inspection = imports.inspect(actor, name, bytes);
        UUID id = UUID.randomUUID();
        var stored = files.store(id, bytes);
        var task =
                new ProjectImportTask(
                        id,
                        actor,
                        old.map(t -> t.version() + 1).orElse(0L),
                        1,
                        inspection.fileName(),
                        stored.bucket(),
                        stored.objectKey(),
                        bytes.length,
                        sha(bytes),
                        inspection,
                        new Configuration(null, null, Map.of()),
                        null,
                        null,
                        null,
                        Instant.now(clock));
        cleanup.release(task, false);
        old.ifPresent(
                previous -> {
                    if (previous.batchId() != null) imports.cancel(actor, previous.batchId());
                    cleanup.release(previous, true);
                });
        tasks.save(task);
        return view(task);
    }

    @Transactional
    public View save(
            UUID actor,
            UUID expectedTaskId,
            long version,
            int step,
            Configuration configuration,
            List<Integer> selected) {
        authorize(actor);
        tasks.lock(actor);
        var old = tasks.findLocked(actor).orElseThrow(ProjectImportWizardService::conflict);
        if (!old.id().equals(expectedTaskId)
                || old.version() != version
                || step < 1
                || step > 3
                || Math.abs(old.step() - step) > 1) throw conflict();
        if (old.batchId() != null
                && !"PREVIEWED"
                        .equals(
                                batches.findByIdAndActorId(old.batchId(), actor)
                                        .orElseThrow(ProjectImportWizardService::conflict)
                                        .status())) throw conflict();
        var config = configuration == null ? old.configuration() : configuration;
        if (config.fieldMapping() == null || config.fieldMapping().size() > 80) throw conflict();
        config = currentConfiguration(config);
        validateConfiguration(old.inspection(), config, step >= 2);
        UUID batch = old.batchId();
        var preview =
                old.previewConfiguration() == null
                        ? null
                        : currentConfiguration(old.previewConfiguration());
        List<Integer> selection = selected;
        if (step == 3 && old.step() == 2) {
            var missing =
                    com.pluto.ndi.projects.util.ProjectImportMappingRules.missing(
                            config.fieldMapping(), List.of());
            if (!missing.isEmpty()) throw new BusinessException(new ImportMappingError(missing));
            try {
                var sourceRows =
                        reader.read(
                                source(old),
                                config.sheetName(),
                                config.headerRowNo(),
                                config.fieldMapping());
                missing =
                        com.pluto.ndi.projects.util.ProjectImportMappingRules.missing(
                                config.fieldMapping(), sourceRows);
                if (!missing.isEmpty())
                    throw new BusinessException(new ImportMappingError(missing));
            } catch (java.io.IOException | IllegalArgumentException failure) {
                throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
            }
        }
        if (step == 3 && old.step() == 2 && !config.equals(preview)) {
            var result =
                    imports.preview(
                            actor,
                            old.fileName(),
                            source(old),
                            config.sheetName(),
                            config.headerRowNo(),
                            config.fieldMapping(),
                            batch);
            batches.flush(); // 后续通过 JDBC 保存外键前，先将预检批次写入数据库。
            batch = result.batchId();
            preview = config;
            selection = null;
        }
        if (step == 3 && batch == null) throw conflict();
        if (selection != null
                && (selection.size() > 1000
                        || new HashSet<>(selection).size() != selection.size()
                        || selection.stream().anyMatch(n -> n == null || n < 1))) throw conflict();
        var next =
                new ProjectImportTask(
                        old.id(),
                        actor,
                        version + 1,
                        step,
                        old.fileName(),
                        old.bucket(),
                        old.objectKey(),
                        old.byteSize(),
                        old.sha256(),
                        old.inspection(),
                        config,
                        selection,
                        preview,
                        batch,
                        Instant.now(clock));
        tasks.save(next);
        return view(next);
    }

    @Transactional
    public void discard(UUID actor, UUID expectedTaskId, long version) {
        authorize(actor);
        tasks.lock(actor);
        var task = tasks.findLocked(actor).orElseThrow(ProjectImportWizardService::conflict);
        if (!task.id().equals(expectedTaskId) || task.version() != version) throw conflict();
        if (task.batchId() != null) imports.cancel(actor, task.batchId());
        tasks.delete(actor);
        cleanup.release(task, true);
    }

    private void validateConfiguration(
            ProjectExcelReader.WorkbookInspection inspection,
            Configuration config,
            boolean required) {
        if (config.sheetName() == null || config.headerRowNo() == null) {
            if (required) throw conflict();
            return;
        }
        var header =
                inspection.sheets().stream()
                        .filter(s -> s.name().equals(config.sheetName()))
                        .flatMap(s -> s.headerCandidates().stream())
                        .filter(h -> h.rowNo() == config.headerRowNo())
                        .findFirst()
                        .orElseThrow(ProjectImportWizardService::conflict);
        var used = new HashSet<String>();
        for (var mapping : config.fieldMapping().entrySet()) {
            if (!inspection.fields().contains(mapping.getKey()) || mapping.getValue() == null)
                throw conflict();
            if (mapping.getValue().isBlank()) continue;
            if (!header.headers().contains(mapping.getValue()) || !used.add(mapping.getValue()))
                throw conflict();
        }
    }

    private Configuration currentConfiguration(Configuration config) {
        return new Configuration(
                config.sheetName(),
                config.headerRowNo(),
                ProjectExcelReader.withoutLegacyCategory(config.fieldMapping()));
    }

    private View view(ProjectImportTask task) {
        return new View(
                task.id(),
                task.version(),
                task.step(),
                task.fileName(),
                new ProjectExcelReader.WorkbookInspection(
                        task.inspection().fileName(),
                        ProjectExcelReader.mappableHeaders(),
                        task.inspection().sheets()),
                currentConfiguration(task.configuration()),
                task.selectedRows(),
                task.batchId() == null ? null : imports.find(task.actorId(), task.batchId()),
                task.updatedAt(),
                fieldRules(ProjectExcelReader.mappableHeaders()));
    }

    private List<FieldRule> fieldRules(List<String> fields) {
        var required = Set.of("省分", "地市", "客户名称", "客户所属行业", "项目名称", "项目建设内容", "项目阶段", "项目总金额（万）");
        return fields.stream()
                .map(
                        field -> {
                            String rule = required.contains(field) ? "REQUIRED" : "OPTIONAL",
                                    hint = "未提供时可留空";
                            if (required.contains(field)) hint = "每行必填，可在预检中修正";
                            if (field.equals("项目负责人")) {
                                rule = "REQUIRED";
                                hint = "至少一位，分号分隔；兼容旧三类人员列";
                            }
                            if (field.endsWith("预计金额（万）")) {
                                rule = "CONDITIONAL";
                                hint = "四项至少填写一项；有值（含 0）自动识别该类别，空值不计入，合计不得超过总金额";
                            }
                            if (Set.of("合同编号", "签约金额（万）").contains(field)) {
                                rule = "CONDITIONAL";
                                hint = "计收阶段必填；签约/终止填写任一项时须成对填写，编号全局唯一";
                            }
                            return new FieldRule(field, rule, hint);
                        })
                .toList();
    }

    private byte[] source(ProjectImportTask task) {
        var bytes = files.read(task.bucket(), task.objectKey());
        if (bytes.length != task.byteSize() || !sha(bytes).equals(task.sha256()))
            throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        return bytes;
    }

    private String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessException conflict() {
        return new BusinessException(ProjectErrorCode.IMPORT_TASK_CONFLICT);
    }
}
