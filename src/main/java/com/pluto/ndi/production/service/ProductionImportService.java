package com.pluto.ndi.production.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.production.domain.ProductionImport.*;
import com.pluto.ndi.production.mapper.ProductionImportMapper;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.web.BusinessException;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class ProductionImportService {
    private final ProductionAccessService access;
    private final ProductionPlanService plans;
    private final ProductionImportMapper tasks;
    private final ImportWorkbookDirectory files;
    private final OrganizationDirectory organizations;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditWriter audit;

    public ProductionImportService(
            ProductionAccessService access,
            ProductionPlanService plans,
            ProductionImportMapper tasks,
            ImportWorkbookDirectory files,
            OrganizationDirectory organizations,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        this.access = access;
        this.plans = plans;
        this.tasks = tasks;
        this.files = files;
        this.organizations = organizations;
        this.json = json;
        this.clock = clock;
        this.audit = audit;
    }

    private void authorize(UUID actor) {
        if (!access.require(actor).writable())
            throw new BusinessException(ProductionError.forbidden());
    }

    @Transactional(readOnly = true)
    public Task current(UUID actor) {
        authorize(actor);
        return tasks.find(actor).map(ProductionImportMapper.Stored::task).orElse(null);
    }

    public Task upload(UUID actor, String fileName, byte[] data, Long version, UUID expected) {
        authorize(actor);
        tasks.lock(actor);
        var old = tasks.find(actor);
        if (old.isPresent()
                        && (version == null
                                || expected == null
                                || version != old.get().task().version()
                                || !expected.equals(old.get().task().id()))
                || old.isEmpty() && (version != null || expected != null)) throw conflict();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw invalid("请选择 .xlsx 文件");
        try {
            var inspection = ExcelWorkbook.inspect(fileName, data, ProductionImport.fields());
            var id = UUID.randomUUID();
            var stored = files.storeFor("production", id, data);
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
                            ProductionImport.rules());
            old.ifPresent(previous -> cleanup(previous.bucket(), previous.key(), true));
            tasks.save(
                    actor,
                    new ProductionImportMapper.Stored(
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
        if (step < 1
                || step > 3
                || Math.abs(step - old.step()) > 1
                || config == null
                || config.fieldMapping() == null
                || config.fieldMapping().size() > 64) throw conflict();
        Batch batch = old.batch();
        if (step == 3 && old.step() == 2) {
            var missing =
                    new ArrayList<>(
                            ProductionImport.REQUIRED.stream()
                                    .filter(
                                            f ->
                                                    config.fieldMapping()
                                                            .getOrDefault(f, "")
                                                            .isBlank())
                                    .toList());
            if (ProductionImport.AMOUNTS.stream()
                    .noneMatch(f -> !config.fieldMapping().getOrDefault(f, "").isBlank()))
                missing.add("至少一项排产金额");
            if (!missing.isEmpty()) throw invalid("请先映射必填字段：" + String.join("、", missing));
            try {
                var data = files.read(stored.bucket(), stored.key());
                if (data.length != stored.size() || !sha(data).equals(stored.sha()))
                    throw invalid("源文件完整性校验失败");
                var lines =
                        ExcelWorkbook.read(
                                data,
                                config.sheetName(),
                                config.headerRowNo(),
                                config.fieldMapping(),
                                ProductionImport.fields());
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
        if (selected != null
                && (selected.size() > 1000
                        || new HashSet<>(selected).size() != selected.size()
                        || selected.stream().anyMatch(n -> n == null || n < 1)))
            throw invalid("选中行无效");
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
                || !ProductionImport.fields().containsAll(changes.keySet())
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
            throw invalid("至少选择一条通过预检的排产记录");
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
                var plan = plans.create(actor, command(cells(row)));
                result.add(
                        new Row(
                                row.rowNumber(),
                                "IMPORTED",
                                "[]",
                                plan.id(),
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
                "PRODUCTION_IMPORT",
                batch,
                "PRODUCTION_IMPORT_COMPLETED",
                Map.of(
                        "importedRows",
                        selection.size(),
                        "discardedRows",
                        result.size() - selection.size()));
        return completed;
    }

    private Batch validateRows(UUID actor, UUID batch, List<Row> source) {
        var result = new ArrayList<Row>();
        var seen = new HashSet<String>();
        int valid = 0;
        for (var row : source) {
            var errors = new ArrayList<String>();
            try {
                var command = plans.validate(actor, command(cells(row)));
                var key =
                        command.planMonth()
                                + "|"
                                + command.provinceId()
                                + "|"
                                + command.cityId()
                                + "|"
                                + com.pluto.ndi.production.util.ProductionRules.normalize(
                                        command.projectName())
                                + "|"
                                + com.pluto.ndi.production.util.ProductionRules.normalize(
                                        command.customerName());
                if (!seen.add(key)) errors.add("与文件内前序记录重复：同月份、同省市、同项目及客户只允许一条");
                if (plans.duplicate(command)) errors.add("系统内已存在相同月份、省市、项目及客户的排产记录");
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

    private ProductionPlan.Command command(Map<String, String> c) {
        var location =
                organizations
                        .findEnabledLocationByNames(c.get("省分"), c.get("地市"))
                        .orElseThrow(() -> invalid("省分或地市未在系统启用，或地市与省分不匹配"));
        var codes = new LinkedHashSet<String>();
        for (var name : c.getOrDefault("三化类别", "").split("[;；,，、]")) {
            var code =
                    switch (name.strip()) {
                        case "网络化", "工业专网", "NETWORK" -> "NETWORK";
                        case "格物", "数字化（格物）", "GEWU" -> "GEWU";
                        case "大数据", "工业大数据", "BIG_DATA" -> "BIG_DATA";
                        case "万悟", "智能化", "WANGWU" -> "WANGWU";
                        default -> "";
                    };
            if (code.isEmpty()) throw invalid("三化类别须为网络化、格物、大数据或万悟");
            codes.add(code);
        }
        String month =
                c.getOrDefault("排产月份", "")
                        .strip()
                        .replace("年", "-")
                        .replace("月", "")
                        .replace("/", "-");
        if (month.matches("[0-9]{4}-[0-9]"))
            month = month.substring(0, 5) + "0" + month.substring(5);
        if (month.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) month = month.substring(0, 7);
        return new ProductionPlan.Command(
                c.get("接口人"),
                month,
                location.provinceId(),
                location.cityId(),
                c.get("项目名称"),
                c.get("客户名称"),
                List.copyOf(codes),
                amount(c, 0),
                amount(c, 1),
                amount(c, 2),
                amount(c, 3),
                c.get("项目状态"));
    }

    private BigDecimal amount(Map<String, String> c, int index) {
        var value = c.getOrDefault(ProductionImport.AMOUNTS.get(index), "").strip();
        if (value.isEmpty()) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw invalid(ProductionImport.AMOUNTS.get(index) + "须为数字");
        }
    }

    private ProductionImportMapper.Stored require(UUID actor, UUID id, long version) {
        var stored = tasks.find(actor).orElseThrow(ProductionImportService::conflict);
        if (!stored.task().id().equals(id) || stored.task().version() != version) throw conflict();
        return stored;
    }

    private ProductionImportMapper.Stored batchTask(UUID actor, UUID batch) {
        var stored = tasks.find(actor).orElseThrow(ProductionImportService::conflict);
        if (stored.task().batch() == null || !stored.task().batch().batchId().equals(batch))
            throw conflict();
        return stored;
    }

    private ProductionImportMapper.Stored with(ProductionImportMapper.Stored old, Task task) {
        return new ProductionImportMapper.Stored(
                task, old.bucket(), old.key(), old.size(), old.sha());
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
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanup(String bucket, String key, boolean afterCommit) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    public void afterCompletion(int status) {
                        if ((status == STATUS_COMMITTED) == afterCommit)
                            try {
                                files.delete(bucket, key);
                            } catch (Exception e) {
                                LoggerFactory.getLogger(ProductionImportService.class)
                                        .warn("排产导入文件清理失败 {}", key, e);
                            }
                    }
                });
    }

    private static BusinessException invalid(String text) {
        return new BusinessException(ProductionError.invalid(text));
    }

    private static BusinessException conflict() {
        return new BusinessException(ProductionError.conflict());
    }
}
