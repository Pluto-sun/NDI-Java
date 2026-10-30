package com.pluto.ndi.shared.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

import java.time.*;
import java.util.*;

@Transactional
public abstract class TabularImportEngine<C> {
    private final ImportTaskStore tasks;
    private final ImportSourceStore files;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditWriter audit;
    private final String module;

    protected TabularImportEngine(
            ImportTaskStore tasks,
            ImportSourceStore files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit,
            String module) {
        this.tasks = tasks;
        this.files = files;
        this.json = json;
        this.clock = clock;
        this.audit = audit;
        this.module = module;
    }

    protected abstract void authorize(UUID actor);

    protected abstract List<String> fields();

    protected abstract List<Rule> rules();

    protected abstract List<String> missing(Map<String, String> mapping);

    protected abstract C validate(UUID actor, Map<String, String> cells);

    protected abstract Object duplicateKey(C value);

    protected abstract boolean duplicate(C value);

    protected abstract UUID create(UUID actor, Map<String, String> cells);

    protected abstract BusinessException invalid(String message);

    protected abstract BusinessException conflict();

    protected List<ExcelWorkbook.Line> read(byte[] bytes, Configuration config)
            throws java.io.IOException {
        return ExcelWorkbook.read(
                bytes, config.sheetName(), config.headerRowNo(), config.fieldMapping(), fields());
    }

    @Transactional
    public Task current(UUID actor) {
        authorize(actor);
        return tasks.find(actor).map(ImportTaskStore.Stored::task).orElse(null);
    }

    public Task upload(UUID actor, String fileName, byte[] data, Long version, UUID expected) {
        authorize(actor);
        tasks.lock(actor);
        var old = tasks.find(actor);
        if (old.isPresent()
                        && (old.get().task().step() != 1
                                || version == null
                                || expected == null
                                || version != old.get().task().version()
                                || !expected.equals(old.get().task().id()))
                || old.isEmpty() && (version != null || expected != null)) throw conflict();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw invalid("请选择 .xlsx 文件");
        try {
            var inspection = ExcelWorkbook.inspect(fileName, data, fields());
            var id = UUID.randomUUID();
            var stored = files.storeFor(module, id, data);
            cleanup(stored.bucket(), stored.objectKey(), false);
            var task =
                    new Task(
                            id,
                            0,
                            1,
                            fileName,
                            inspection,
                            new Configuration(null, null, Map.of()),
                            null,
                            null,
                            Instant.now(clock),
                            rules());
            old.ifPresent(previous -> cleanup(previous.bucket(), previous.key(), true));
            tasks.save(
                    actor,
                    new ImportTaskStore.Stored(
                            task, stored.bucket(), stored.objectKey(), data.length, sha(data)));
            return task;
        } catch (java.io.IOException | IllegalArgumentException failure) {
            throw invalid("无法读取工作簿：" + failure.getMessage());
        }
    }

    public Task save(
            UUID actor,
            UUID id,
            long version,
            int step,
            Configuration config,
            List<Integer> selected) {
        authorize(actor);
        tasks.lock(actor);
        var stored = require(actor, id, version);
        var old = stored.task();
        if (!ImportWorkflow.validStep(old.step(), step)
                || config == null
                || config.fieldMapping() == null
                || config.fieldMapping().size() > 64) throw conflict();
        Batch batch = old.batch();
        if (step == 3 && old.step() == 2) {
            var missing = missing(config.fieldMapping());
            if (!missing.isEmpty()) throw invalid("请先映射必填字段：" + String.join("、", missing));
            try {
                var data = files.read(stored.bucket(), stored.key());
                if (data.length != stored.size() || !sha(data).equals(stored.sha()))
                    throw invalid("源文件完整性校验失败");
                var lines = read(data, config);
                batch =
                        validateRows(
                                actor,
                                UUID.randomUUID(),
                                lines.stream()
                                        .map(
                                                line ->
                                                        new Row(
                                                                line.number(),
                                                                "",
                                                                "[]",
                                                                null,
                                                                encode(line.cells()),
                                                                0,
                                                                null))
                                        .toList());
                selected = null;
            } catch (java.io.IOException | IllegalArgumentException failure) {
                throw invalid("预检失败：" + failure.getMessage());
            }
        }
        if (step == 3 && batch == null) throw conflict();
        if (!ImportWorkflow.validSelection(selected)) throw invalid("选中行无效");
        var next =
                new Task(
                        old.id(),
                        old.version() + 1,
                        step,
                        old.fileName(),
                        old.inspection(),
                        config,
                        selected,
                        batch,
                        Instant.now(clock),
                        old.fieldRules());
        tasks.save(actor, with(stored, next));
        return next;
    }

    public void discard(UUID actor, UUID id, long version) {
        authorize(actor);
        tasks.lock(actor);
        var stored = require(actor, id, version);
        tasks.delete(actor);
        cleanup(stored.bucket(), stored.key(), true);
    }

    public Batch batch(UUID actor, UUID batch) {
        authorize(actor);
        return batchTask(actor, batch).task().batch();
    }

    public Batch revise(
            UUID actor, UUID batch, int rowNo, long revision, Map<String, String> changes) {
        authorize(actor);
        tasks.lock(actor);
        var stored = batchTask(actor, batch);
        var old = stored.task();
        if (changes == null
                || changes.isEmpty()
                || !fields().containsAll(changes.keySet())
                || changes.values().stream().anyMatch(v -> v == null || v.length() > 5000))
            throw invalid("修正字段无效");
        var rows = new ArrayList<Row>();
        boolean found = false;
        for (var row : old.batch().rows()) {
            if (row.rowNumber() != rowNo) {
                rows.add(row);
                continue;
            }
            if (row.revisionNo() != revision) throw conflict();
            found = true;
            var cells = cells(row);
            cells.putAll(changes);
            rows.add(new Row(rowNo, "", "[]", null, encode(cells), revision + 1, null));
        }
        if (!found) throw conflict();
        var nextBatch = validateRows(actor, batch, rows);
        tasks.save(
                actor,
                with(
                        stored,
                        new Task(
                                old.id(),
                                old.version(),
                                old.step(),
                                old.fileName(),
                                old.inspection(),
                                old.configuration(),
                                old.selectedRows(),
                                nextBatch,
                                Instant.now(clock),
                                old.fieldRules())));
        return nextBatch;
    }

    public Batch confirm(UUID actor, UUID batch, List<Integer> selection) {
        authorize(actor);
        tasks.lock(actor);
        var stored = batchTask(actor, batch);
        var old = stored.task();
        if (old.step() != 3
                || selection == null
                || selection.isEmpty()
                || selection.size() > 1000
                || new HashSet<>(selection).size() != selection.size())
            throw invalid("至少选择一条通过预检的记录");
        // 确认时重查范围和库内重复；所有选中行在同一事务完成，避免一项任务产生可续导的半成品。
        var fresh = validateRows(actor, batch, old.batch().rows());
        var byNumber = new HashMap<Integer, Row>();
        fresh.rows().forEach(row -> byNumber.put(row.rowNumber(), row));
        if (selection.stream()
                .anyMatch(
                        n -> !byNumber.containsKey(n) || !"READY".equals(byNumber.get(n).status())))
            throw invalid("所选行已不满足导入条件，请重新预检");
        var result = new ArrayList<Row>();
        for (var row : fresh.rows()) {
            if (selection.contains(row.rowNumber())) {
                var recordId = create(actor, cells(row));
                result.add(
                        new Row(
                                row.rowNumber(),
                                "IMPORTED",
                                "[]",
                                recordId,
                                row.sourceCellsJson(),
                                row.revisionNo(),
                                null));
            } else
                result.add(
                        new Row(
                                row.rowNumber(),
                                "SKIPPED",
                                row.errorsJson(),
                                null,
                                row.sourceCellsJson(),
                                row.revisionNo(),
                                null));
        }
        var completed =
                new Batch(
                        batch,
                        "COMPLETED",
                        result.size(),
                        fresh.validRows(),
                        fresh.failedRows(),
                        result.size() - selection.size(),
                        selection.size(),
                        Instant.now(clock),
                        result);
        tasks.delete(actor);
        cleanup(stored.bucket(), stored.key(), true);
        audit.record(
                actor,
                module.toUpperCase(Locale.ROOT) + "_IMPORT",
                batch,
                module.toUpperCase(Locale.ROOT) + "_IMPORT_COMPLETED",
                Map.of(
                        "importedRows",
                        selection.size(),
                        "discardedRows",
                        result.size() - selection.size(),
                        "sourceFile",
                        old.fileName(),
                        "sourceHash",
                        stored.sha(),
                        "records",
                        result.stream()
                                .filter(row -> "IMPORTED".equals(row.status()))
                                .map(row -> Map.of("row", row.rowNumber(), "id", row.recordId()))
                                .toList()));
        return completed;
    }

    private Batch validateRows(UUID actor, UUID batch, List<Row> source) {
        var result = new ArrayList<Row>();
        var seen = new HashSet<Object>();
        int valid = 0;
        for (var row : source) {
            var errors = new ArrayList<String>();
            try {
                var command = validate(actor, cells(row));
                var key = duplicateKey(command);
                if (!seen.add(key)) errors.add("与文件内前序记录重复：业务唯一键相同");
                if (duplicate(command)) errors.add("系统内已存在相同业务唯一键的记录");
            } catch (BusinessException e) {
                errors.add(e.getMessage());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
            if (errors.isEmpty()) valid++;
            result.add(
                    new Row(
                            row.rowNumber(),
                            errors.isEmpty() ? "READY" : "FAILED",
                            encode(errors),
                            null,
                            row.sourceCellsJson(),
                            row.revisionNo(),
                            null));
        }
        return new Batch(
                batch,
                "PREVIEWED",
                result.size(),
                valid,
                result.size() - valid,
                0,
                0,
                null,
                result);
    }

    private ImportTaskStore.Stored require(UUID actor, UUID id, long version) {
        var stored = tasks.find(actor).orElseThrow(this::conflict);
        if (!stored.task().id().equals(id) || stored.task().version() != version) throw conflict();
        return stored;
    }

    private ImportTaskStore.Stored batchTask(UUID actor, UUID batch) {
        var stored = tasks.find(actor).orElseThrow(this::conflict);
        if (stored.task().batch() == null || !stored.task().batch().batchId().equals(batch))
            throw conflict();
        return stored;
    }

    private ImportTaskStore.Stored with(ImportTaskStore.Stored old, Task task) {
        return new ImportTaskStore.Stored(task, old.bucket(), old.key(), old.size(), old.sha());
    }

    private Map<String, String> cells(Row row) {
        try {
            return json.readValue(
                    row.sourceCellsJson(),
                    json.getTypeFactory()
                            .constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha(byte[] bytes) {
        return ImportWorkflow.sha256(bytes);
    }

    private void cleanup(String bucket, String key, boolean afterCommit) {
        ImportFileLifecycle.release(
                files,
                bucket,
                key,
                afterCommit,
                failure ->
                        LoggerFactory.getLogger(TabularImportEngine.class)
                                .warn("导入源文件清理失败 {}", key, failure));
    }
}
