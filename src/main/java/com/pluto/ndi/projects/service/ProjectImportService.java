package com.pluto.ndi.projects.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectCategory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectNarrative;
import com.pluto.ndi.projects.domain.entity.ProjectImportBatchEntity;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;
import com.pluto.ndi.projects.repository.ProjectImportBatchRepository;
import com.pluto.ndi.projects.repository.ProjectImportRowRepository;
import com.pluto.ndi.projects.repository.ProjectImportRowRevisionRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.util.ProjectExcelReader;
import com.pluto.ndi.projects.util.ProjectExpertNames;
import com.pluto.ndi.projects.util.ProjectImportWorkbookWriter;
import com.pluto.ndi.projects.util.ProjectNameMatcher;
import com.pluto.ndi.projects.util.ProjectNarrativeText;
import com.pluto.ndi.projects.util.ProjectPlanningFields;
import com.pluto.ndi.shared.web.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Excel 导入预检及人工确认；失败行不阻断其他行写入。 */
@Service
public class ProjectImportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectImportService.class);
    private static final Map<String, String> STAGE_LABELS =
            Map.of(
                    "方案交流",
                    "SOLUTION_EXCHANGE",
                    "招投标",
                    "BIDDING",
                    "已中标",
                    "BIDDING",
                    "签约",
                    "SIGNED",
                    "计收",
                    "COLLECTION",
                    "已终止",
                    "TERMINATED");
    private final ProjectExcelReader reader;
    private final ProjectImportWorkbookWriter workbookWriter;
    private final IdentityDirectory identity;
    private final OrganizationDirectory organizations;
    private final ProjectIndustryReferenceRepository industries;
    private final ProjectRepository projects;
    private final ProjectImportBatchRepository batches;
    private final ProjectImportRowRepository rows;
    private final ProjectImportRowRevisionRepository revisions;
    private final ProjectImportWriter writer;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ProjectImportNameReviewService nameReviews;
    private final ProjectImportTaskCleanup taskCleanup;

    public ProjectImportService(
            ProjectExcelReader reader,
            ProjectImportWorkbookWriter workbookWriter,
            IdentityDirectory identity,
            OrganizationDirectory organizations,
            ProjectIndustryReferenceRepository industries,
            ProjectRepository projects,
            ProjectImportBatchRepository batches,
            ProjectImportRowRepository rows,
            ProjectImportRowRevisionRepository revisions,
            ProjectImportWriter writer,
            ObjectMapper json,
            TransactionTemplate transactions,
            Clock clock,
            ProjectImportNameReviewService nameReviews,
            ProjectImportTaskCleanup taskCleanup) {
        this.nameReviews = nameReviews;
        this.taskCleanup = taskCleanup;
        this.reader = reader;
        this.workbookWriter = workbookWriter;
        this.identity = identity;
        this.organizations = organizations;
        this.industries = industries;
        this.projects = projects;
        this.batches = batches;
        this.rows = rows;
        this.revisions = revisions;
        this.writer = writer;
        this.json = json;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public ImportBatchSummary preview(UUID actorId, String fileName, byte[] bytes) {
        return preview(actorId, fileName, bytes, null, null, Map.of());
    }

    public ProjectExcelReader.WorkbookInspection inspect(
            UUID actorId, String fileName, byte[] bytes) {
        identity.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        try {
            return reader.inspect(bytes, safeFileName(fileName));
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        }
    }

    @Transactional
    public ImportBatchSummary preview(
            UUID actorId,
            String fileName,
            byte[] bytes,
            String sheetName,
            Integer headerRowNo,
            Map<String, String> fieldMapping) {
        return preview(actorId, fileName, bytes, sheetName, headerRowNo, fieldMapping, null);
    }

    @Transactional
    public ImportBatchSummary preview(
            UUID actorId,
            String fileName,
            byte[] bytes,
            String sheetName,
            Integer headerRowNo,
            Map<String, String> fieldMapping,
            UUID replacesBatchId) {
        var actor =
                identity.findProjectActor(actorId)
                        .filter(ProjectActor::enabled)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        if (replacesBatchId != null) {
            // 与确认导入共用批次锁；校验失败时整笔事务回滚，原计划不受影响。
            var previous =
                    batches.findByIdAndActorId(replacesBatchId, actorId)
                            .orElseThrow(
                                    () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
            if (previous.importedRows() != 0 || "CONFIRMING".equals(previous.status())) {
                throw new BusinessException(ProjectErrorCode.IMPORT_REMAP_NOT_ALLOWED);
            }
            if (!previous.fileSha256().equals(sha256(bytes))) {
                throw new BusinessException(ProjectErrorCode.IMPORT_REMAP_FILE_MISMATCH);
            }
            previous.supersede();
        }
        List<ProjectExcelReader.ExcelRow> source;
        try {
            source = reader.read(bytes, sheetName, headerRowNo, fieldMapping);
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        }
        if (source.isEmpty()) throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        var output = new ArrayList<ProjectImportRowEntity>();
        Set<String> encountered = new HashSet<>();
        Map<String, String> locationKeys = new HashMap<>();
        for (var row : source) {
            var errors = new ArrayList<String>();
            String key = duplicateKey(row, locationKeys);
            if (key != null && !encountered.add(key)) {
                errors.add("重复项目：仅保留文件中首条同省、同地市、同名称记录");
            }
            ProjectImportCandidate candidate;
            try {
                candidate = normalize(row, actor, errors);
            } catch (IllegalArgumentException exception) {
                errors.add("行内日期或字段格式无效");
                candidate = null;
            }
            output.add(
                    ProjectImportRowEntity.preview(
                            null,
                            row.number(),
                            key,
                            stringify(candidate == null ? row.cells() : candidate),
                            stringify(row.cells()),
                            stringify(errors),
                            errors.isEmpty()));
        }
        int valid = (int) output.stream().filter(row -> "READY".equals(row.status())).count();
        var batch =
                ProjectImportBatchEntity.preview(
                        actorId,
                        safeFileName(fileName),
                        sha256(bytes),
                        output.size(),
                        valid,
                        Instant.now(clock),
                        sheetName,
                        headerRowNo,
                        stringify(fieldMapping));
        batches.save(batch);
        var saved = new ArrayList<ProjectImportRowEntity>();
        for (int i = 0; i < output.size(); i++) {
            var item = output.get(i);
            var raw = source.get(i);
            saved.add(
                    ProjectImportRowEntity.preview(
                            batch.id(),
                            item.rowNumber(),
                            item.normalizedKey(),
                            item.rowPayload(),
                            item.sourcePayload(),
                            item.errors(),
                            "READY".equals(item.status())));
        }
        var managedRows = rows.saveAll(saved);
        nameReviews.refresh(actorId, managedRows);
        return summary(batch, managedRows);
    }

    public ImportBatchSummary find(UUID actorId, UUID batchId) {
        // 确认结束后也会由本类内部读取结果，显式事务确保此路径同样能取得批次锁。
        return transactions.execute(
                status -> {
                    var batch =
                            batches.findByIdAndActorId(batchId, actorId)
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ProjectErrorCode.IMPORT_NOT_FOUND));
                    var batchRows = rows.findLockedByBatchId(batchId);
                    refreshLegacyCategories(batch, batchRows);
                    return summary(batch, batchRows);
                });
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchListItem> findMine(UUID actorId, Pageable pageable) {
        identity.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return batches.findByActorIdAndStatusInOrderByCreatedAtDescIdDesc(
                        actorId, List.of("PREVIEWED", "CONFIRMING"), pageable)
                .map(ImportBatchListItem::from);
    }

    public byte[] template(UUID actorId) {
        identity.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return workbookWriter.template();
    }

    @Transactional(readOnly = true)
    public byte[] failedRows(UUID actorId, UUID batchId) {
        batches.findById(batchId)
                .filter(item -> item.actorId().equals(actorId))
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        return workbookWriter.failedRows(rows.findByBatchIdOrderByRowNumber(batchId));
    }

    @Transactional
    public ImportBatchSummary cancel(UUID actorId, UUID batchId) {
        var batch =
                batches.findByIdAndActorId(batchId, actorId)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (!"PREVIEWED".equals(batch.status())) {
            throw new BusinessException(ProjectErrorCode.IMPORT_NOT_PREVIEWED);
        }
        batch.cancel();
        return summary(batch, rows.findByBatchIdOrderByRowNumber(batchId));
    }

    @Transactional
    public ImportBatchSummary reviseRow(
            UUID actorId,
            UUID batchId,
            int rowNumber,
            int expectedRevision,
            Map<String, String> changes) {
        var actor =
                identity.findProjectActor(actorId)
                        .filter(ProjectActor::enabled)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var batch =
                batches.findByIdAndActorId(batchId, actorId)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (!"PREVIEWED".equals(batch.status())) {
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_NOT_EDITABLE);
        }
        var item =
                rows.findByBatchIdAndRowNumber(batchId, rowNumber)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (item.sourcePayload() == null
                || !"READY".equals(item.status()) && !"FAILED".equals(item.status())) {
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_NOT_EDITABLE);
        }
        if (item.revisionNo() != expectedRevision) {
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_VERSION_CONFLICT);
        }
        if (changes == null || changes.isEmpty() || changes.size() > 64) {
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_INVALID);
        }
        Map<String, String> source;
        try {
            source = json.readValue(item.sourcePayload(), new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_NOT_EDITABLE);
        }
        var corrected = new HashMap<>(source);
        for (var change : changes.entrySet()) {
            if ("三化类别".equals(change.getKey())
                    || !source.containsKey(change.getKey())
                    || change.getValue() == null
                    || change.getValue().length() > 4000) {
                throw new BusinessException(ProjectErrorCode.IMPORT_ROW_INVALID);
            }
            corrected.put(change.getKey(), change.getValue().strip());
        }
        if (corrected.equals(source))
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_INVALID);
        var excelRow = new ProjectExcelReader.ExcelRow(rowNumber, Map.copyOf(corrected));
        var errors = new ArrayList<String>();
        var key = duplicateKey(excelRow);
        if (key != null
                && rows.findByBatchIdOrderByRowNumber(batchId).stream()
                        .anyMatch(
                                previous ->
                                        previous.rowNumber() < rowNumber
                                                && key.equals(previous.normalizedKey()))) {
            errors.add("重复项目：仅保留文件中首条同省、同地市、同名称记录");
        }
        ProjectImportCandidate candidate;
        try {
            candidate = normalize(excelRow, actor, errors);
        } catch (IllegalArgumentException exception) {
            errors.add("行内日期或字段格式无效");
            candidate = null;
        }
        String before = item.sourcePayload();
        item.revise(
                stringify(corrected),
                stringify(candidate == null ? corrected : candidate),
                key,
                stringify(errors),
                errors.isEmpty());
        revisions.save(
                com.pluto.ndi.projects.domain.entity.ProjectImportRowRevisionEntity.create(
                        item, actorId, before, Instant.now(clock)));
        var allRows = rows.findByBatchIdOrderByRowNumber(batchId);
        refreshDuplicateRows(actor, allRows);
        nameReviews.refresh(actorId, allRows);
        batch.refreshPreviewCounts(
                (int) allRows.stream().filter(row -> "READY".equals(row.status())).count());
        return summary(batch, allRows);
    }

    @Transactional
    public ImportBatchSummary decideName(
            UUID actorId,
            UUID batchId,
            int rowNumber,
            int revision,
            boolean confirmed,
            String reason,
            String fingerprint) {
        identity.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var batch =
                batches.findByIdAndActorId(batchId, actorId)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (!"PREVIEWED".equals(batch.status()))
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_NOT_EDITABLE);
        var row =
                rows.findByBatchIdAndRowNumber(batchId, rowNumber)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (row.revisionNo() != revision)
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_VERSION_CONFLICT);
        var review = nameReviews.read(row);
        String explanation = reason == null ? null : reason.strip();
        if (review == null || !review.fingerprint().equals(fingerprint))
            throw new BusinessException(ProjectErrorCode.IMPORT_ROW_VERSION_CONFLICT);
        if (!confirmed
                || explanation != null && explanation.length() > 1000
                || "STRONG".equals(review.level())
                        && (explanation == null || explanation.length() < 5))
            throw new BusinessException(ProjectErrorCode.IMPORT_NAME_REVIEW_REQUIRED);
        var updated =
                new com.pluto.ndi.projects.domain.ProjectImportNameReview(
                        review.provinceId(),
                        review.cityId(),
                        review.level(),
                        review.score(),
                        review.fingerprint(),
                        review.evidence(),
                        review.matches(),
                        true,
                        explanation);
        var cells = new HashMap<>(nameReviews.source(row));
        String before = row.sourcePayload();
        cells.put("__nameReviewReason", explanation == null ? "" : explanation);
        cells.put("__nameReviewFingerprint", review.fingerprint());
        row.revise(
                stringify(cells),
                row.rowPayload(),
                row.normalizedKey(),
                row.errors(),
                "READY".equals(row.status()));
        row.nameReview(stringify(updated));
        revisions.save(
                com.pluto.ndi.projects.domain.entity.ProjectImportRowRevisionEntity.create(
                        row, actorId, before, Instant.now(clock)));
        return summary(batch, rows.findByBatchIdOrderByRowNumber(batchId));
    }

    public ImportBatchSummary confirm(UUID actorId, UUID batchId) {
        return confirm(actorId, batchId, null);
    }

    public ImportBatchSummary confirm(
            UUID actorId, UUID batchId, List<Integer> selectedRowNumbers) {
        var batch =
                transactions.execute(
                        status -> {
                            var locked =
                                    batches.findByIdAndActorId(batchId, actorId)
                                            .orElseThrow(
                                                    () ->
                                                            new BusinessException(
                                                                    ProjectErrorCode
                                                                            .IMPORT_NOT_FOUND));
                            if (!"PREVIEWED".equals(locked.status())) {
                                if ("CANCELLED".equals(locked.status())) {
                                    throw new BusinessException(
                                            ProjectErrorCode.IMPORT_NOT_PREVIEWED);
                                }
                                throw new BusinessException(
                                        ProjectErrorCode.IMPORT_ALREADY_CONFIRMED);
                            }
                            var batchRows = rows.findByBatchIdOrderByRowNumber(batchId);
                            refreshLegacyCategories(locked, batchRows);
                            var ready =
                                    batchRows.stream()
                                            .filter(row -> "READY".equals(row.status()))
                                            .map(ProjectImportRowEntity::rowNumber)
                                            .collect(java.util.stream.Collectors.toSet());
                            var selected =
                                    selectedRowNumbers == null
                                            ? ready
                                            : new HashSet<>(selectedRowNumbers);
                            if (selected.isEmpty()
                                    || selectedRowNumbers != null
                                            && (selected.size() != selectedRowNumbers.size()
                                                    || !ready.containsAll(selected))) {
                                throw new BusinessException(
                                        ProjectErrorCode.IMPORT_SELECTION_INVALID);
                            }
                            for (var row : batchRows)
                                if (selected.contains(row.rowNumber())) {
                                    var review = nameReviews.read(row);
                                    if (review != null && !review.accepted())
                                        throw new BusinessException(
                                                ProjectErrorCode.IMPORT_NAME_REVIEW_REQUIRED);
                                }
                            batchRows.stream()
                                    .filter(
                                            row ->
                                                    !selected.contains(row.rowNumber())
                                                            && Set.of("READY", "FAILED")
                                                                    .contains(row.status()))
                                    .forEach(ProjectImportRowEntity::skip);
                            locked.beginConfirmation(Instant.now(clock));
                            return locked;
                        });
        return writeRemainingRows(actorId, batch.id());
    }

    public ImportBatchSummary resume(UUID actorId, UUID batchId) {
        var batch =
                batches.findById(batchId)
                        .filter(item -> item.actorId().equals(actorId))
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (!"CONFIRMING".equals(batch.status())) {
            throw new BusinessException(ProjectErrorCode.IMPORT_ALREADY_CONFIRMED);
        }
        if (batch.confirmedAt() == null
                || batch.confirmedAt().isAfter(Instant.now(clock).minus(Duration.ofMinutes(15)))) {
            throw new BusinessException(ProjectErrorCode.IMPORT_RECOVERY_NOT_READY);
        }
        return writeRemainingRows(actorId, batchId);
    }

    public void recoverStale() {
        var stale =
                batches.findTop20ByStatusAndConfirmedAtBeforeOrderByConfirmedAtAsc(
                        "CONFIRMING", Instant.now(clock).minus(Duration.ofMinutes(15)));
        for (var batch : stale) {
            try {
                resume(batch.actorId(), batch.id());
            } catch (BusinessException exception) {
                if (exception.errorCode() != ProjectErrorCode.IMPORT_ALREADY_CONFIRMED
                        && exception.errorCode() != ProjectErrorCode.IMPORT_RECOVERY_NOT_READY) {
                    LOGGER.error("导入批次恢复失败，批次编号：{}", batch.id(), exception);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("导入批次恢复失败，批次编号：{}", batch.id(), exception);
            }
        }
    }

    private ImportBatchSummary writeRemainingRows(UUID actorId, UUID batchId) {
        var sourceRows = rows.findByBatchIdOrderByRowNumber(batchId);
        for (var row : sourceRows) {
            if (!"READY".equals(row.status())) continue;
            try {
                transactions.execute(status -> writer.importRow(actorId, row.id()));
            } catch (RuntimeException exception) {
                String reason =
                        exception instanceof IllegalStateException && exception.getMessage() != null
                                ? exception.getMessage()
                                : "确认写入失败，请核查该行的目标约束";
                transactions.executeWithoutResult(
                        status -> {
                            var locked = rows.lockById(row.id()).orElseThrow();
                            if ("READY".equals(locked.status()))
                                locked.fail(stringify(List.of(reason)));
                        });
            }
        }
        transactions.executeWithoutResult(
                status -> {
                    var batch = batches.findByIdAndActorId(batchId, actorId).orElseThrow();
                    if (!"CONFIRMING".equals(batch.status())) return;
                    var latest = rows.findByBatchIdOrderByRowNumber(batchId);
                    if (latest.stream().anyMatch(row -> "READY".equals(row.status()))) return;
                    int skipped =
                            (int)
                                    latest.stream()
                                            .filter(row -> "SKIPPED".equals(row.status()))
                                            .count();
                    int imported =
                            (int)
                                    latest.stream()
                                            .filter(row -> "IMPORTED".equals(row.status()))
                                            .count();
                    batch.finish(imported, skipped);
                });
        var result = find(actorId, batchId);
        if ("COMPLETED".equals(result.status())) taskCleanup.completed(batchId);
        return result;
    }

    private ProjectImportCandidate normalize(
            ProjectExcelReader.ExcelRow row, ProjectActor actor, List<String> errors) {
        required(row, errors, "省分", "地市", "客户名称", "客户所属行业", "项目名称", "项目建设内容", "项目阶段");
        String projectName = row.get("项目名称");
        if (projectName.length() < 2
                || projectName.length() > 200
                || ProjectNameMatcher.standard(projectName).isEmpty())
            errors.add("项目名称须为2至200字符，且包含文字或数字");
        List<String> responsibleNames = null;
        boolean unified = !row.get("项目负责人").isBlank();
        if (unified) {
            try {
                responsibleNames = ProjectResponsiblePersonStore.names(row.get("项目负责人"));
            } catch (BusinessException error) {
                errors.add(error.errorCode().message());
            }
        } else if (row.get("省分负责人").isBlank()
                && row.get("地市客户经理").isBlank()
                && row.get("产品单位支撑专家").isBlank()) {
            errors.add("项目负责人不能为空");
        }
        var location = organizations.findEnabledLocationByNames(row.get("省分"), row.get("地市"));
        if (location.isEmpty())
            errors.add(
                    "省分或地市未在新系统启用或名称不唯一："
                            + row.get("省分")
                            + " / "
                            + row.get("地市")
                            + "；请按行政区划核对原文件，或在预检中修正该行");
        else if (!identity.canImportProject(
                actor, location.get().provinceId(), location.get().cityId())) {
            errors.add("无此省市项目导入权限");
        }
        var industry = industries.findByIndustryNameAndEnabledTrue(row.get("客户所属行业"));
        if (industry.isEmpty()) errors.add("行业名称未匹配有效代码");
        var leader =
                location.flatMap(
                        value ->
                                identity.findEligibleProvinceLeaderByName(
                                        row.get("省分负责人"), value.provinceId()));
        // 兼容旧模板；仅旧省分负责人列尝试匹配账号，新名单绝不按姓名自动绑定。
        var stage = STAGE_LABELS.get(row.get("项目阶段"));
        if (stage == null) errors.add("项目阶段未识别");
        var signingSubject =
                switch (row.get("签约主体")) {
                    case "", "省分签约", "省分" -> "PROVINCE_SIGN";
                    case "数科签约", "数科" -> "DS_SIGN";
                    default -> null;
                };
        if (signingSubject == null) errors.add("签约主体未识别");
        String contractNo = row.get("合同编号").strip();
        BigDecimal signedAmount =
                row.get("签约金额（万）").isBlank() ? null : amount(row.get("签约金额（万）"), "签约金额", errors);
        if ("COLLECTION".equals(stage) || !contractNo.isBlank() || signedAmount != null) {
            if (contractNo.isBlank()) errors.add("合同编号不能为空");
            if (signedAmount == null && row.get("签约金额（万）").isBlank()) errors.add("签约金额不能为空");
            if ("SOLUTION_EXCHANGE".equals(stage) || "BIDDING".equals(stage)) {
                errors.add("方案交流和招投标阶段不能填写合同信息");
            }
        }
        if (contractNo.length() > 100) errors.add("合同编号不得超过100个字符");
        if (!contractNo.isBlank() && projects.existsByContractNo(contractNo)) errors.add("合同编号已存在");
        var total = amount(row.get("项目总金额（万）"), "项目总金额", errors);
        var categories = new ArrayList<ProjectImportCandidate.CategoryAmount>();
        boolean hasCategoryAmount = false;
        for (var category : ProjectCategory.values()) {
            var raw =
                    row.get(
                            com.pluto.ndi.projects.util.ProjectImportAmounts.HEADERS.get(
                                    category.name()));
            if (raw.isBlank()) continue;
            hasCategoryAmount = true;
            var categoryAmount = amount(raw, category.label() + "金额", errors);
            if (categoryAmount != null)
                categories.add(
                        new ProjectImportCandidate.CategoryAmount(category.name(), categoryAmount));
        }
        if (!hasCategoryAmount) errors.add("至少填写一项三化预计金额，0 也表示包含该类别");
        if (total != null
                && categories.stream()
                                .map(ProjectImportCandidate.CategoryAmount::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .compareTo(total)
                        > 0) {
            errors.add("三化金额合计超过项目总金额");
        }
        List<String> expertNames = List.of();
        List<String> scenarios = List.of();
        String bidMonth = null;
        try {
            expertNames = ProjectExpertNames.parse(row.get("产品单位支撑专家"));
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
        try {
            scenarios = ProjectPlanningFields.parseScenarios(row.get("应用场景"));
            var parsedMonth = ProjectPlanningFields.parseBidMonth(row.get("拟招标日期"));
            bidMonth =
                    parsedMonth == null ? null : java.time.YearMonth.from(parsedMonth).toString();
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
        ProjectNarrative narrative = null;
        try {
            narrative =
                    ProjectNarrativeText.normalize(
                            new ProjectNarrative(
                                    row.get("项目建设内容"), row.get("产品推广内容"), row.get("备注")),
                            row.get("项目建设内容"));
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
        com.pluto.ndi.projects.domain.ProjectImportAttributes importAttributes = null;
        try {
            importAttributes =
                    new com.pluto.ndi.projects.domain.ProjectImportAttributes(
                            row.get("重要度"), row.get("支撑行业专家"));
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
        if (!errors.isEmpty()) return null;
        var progress =
                ProjectExcelReader.progressColumns(row.cells()).stream()
                        .map(
                                item ->
                                        new ProjectImportCandidate.Progress(
                                                item.occurredAt(), item.content()))
                        .toList();
        return new ProjectImportCandidate(
                location.get().provinceId(),
                location.get().cityId(),
                row.get("项目名称"),
                row.get("客户名称"),
                industry.get().industryCode(),
                row.get("项目建设内容"),
                total,
                unified ? null : leader.orElse(null),
                unified ? "" : row.get("地市客户经理"),
                signingSubject,
                stage,
                List.copyOf(categories),
                progress,
                Instant.now(clock),
                unified ? List.of() : expertNames,
                bidMonth,
                scenarios,
                narrative,
                contractNo.isBlank() ? null : contractNo,
                signedAmount,
                importAttributes,
                responsibleNames,
                unified ? null : row.get("省分负责人"));
    }

    private void required(ProjectExcelReader.ExcelRow row, List<String> errors, String... headers) {
        for (var header : headers) if (row.get(header).isBlank()) errors.add(header + "不能为空");
    }

    private BigDecimal amount(String raw, String field, List<String> errors) {
        if (raw.isBlank()) {
            errors.add(field + "不能为空");
            return null;
        }
        try {
            var number = new BigDecimal(raw.replace(",", ""));
            if (number.signum() < 0
                    || number.scale() > 2
                    || number.precision() - number.scale() > 16) {
                errors.add(field + "超出金额范围或小数超过两位");
                return null;
            }
            return number;
        } catch (NumberFormatException exception) {
            errors.add(field + "不是有效金额");
            return null;
        }
    }

    private String duplicateKey(ProjectExcelReader.ExcelRow row) {
        return duplicateKey(row, new HashMap<>());
    }

    private String duplicateKey(ProjectExcelReader.ExcelRow row, Map<String, String> locations) {
        if (row.get("省分").isBlank() || row.get("地市").isBlank() || row.get("项目名称").isBlank())
            return null;
        String raw = row.get("省分") + "\n" + row.get("地市");
        String location =
                locations.computeIfAbsent(
                        raw,
                        key ->
                                organizations
                                        .findEnabledLocationByNames(row.get("省分"), row.get("地市"))
                                        .map(value -> value.cityId().toString())
                                        .orElse(raw));
        return location + ":" + ProjectNameMatcher.standard(row.get("项目名称"));
    }

    /** 旧的未确认预检按新规则重新计算，保留行修正、名称确认与原步骤。 */
    private void refreshLegacyCategories(
            ProjectImportBatchEntity batch, List<ProjectImportRowEntity> items) {
        if (!"PREVIEWED".equals(batch.status())
                || items.stream()
                        .noneMatch(
                                row ->
                                        row.sourcePayload() != null
                                                && nameReviews.source(row).containsKey("三化类别")))
            return;
        var actor =
                identity.findProjectActor(batch.actorId())
                        .filter(ProjectActor::enabled)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var seen = new HashSet<String>();
        var locations = new HashMap<String, String>();
        for (var row : items) {
            if (!Set.of("READY", "FAILED").contains(row.status()) || row.sourcePayload() == null)
                continue;
            var source = new HashMap<>(nameReviews.source(row));
            source.remove("三化类别");
            var excel = new ProjectExcelReader.ExcelRow(row.rowNumber(), Map.copyOf(source));
            var errors = new ArrayList<String>();
            var key = duplicateKey(excel, locations);
            if (key != null && !seen.add(key)) errors.add("重复项目：仅保留文件中首条同省、同地市、同名称记录");
            ProjectImportCandidate candidate = null;
            try {
                candidate = normalize(excel, actor, errors);
            } catch (IllegalArgumentException failure) {
                errors.add("行内日期或字段格式无效");
            }
            var before = row.sourcePayload();
            row.revise(
                    stringify(source),
                    stringify(candidate == null ? source : candidate),
                    key,
                    stringify(errors),
                    errors.isEmpty());
            revisions.save(
                    com.pluto.ndi.projects.domain.entity.ProjectImportRowRevisionEntity.create(
                            row, batch.actorId(), before, Instant.now(clock)));
        }
        nameReviews.refresh(batch.actorId(), items);
        batch.refreshPreviewCounts(
                (int) items.stream().filter(row -> "READY".equals(row.status())).count());
    }

    private void refreshDuplicateRows(ProjectActor actor, List<ProjectImportRowEntity> batchRows) {
        var seen = new HashSet<String>();
        var locations = new HashMap<String, String>();
        for (var row : batchRows) {
            if (!Set.of("READY", "FAILED").contains(row.status())) continue;
            var source = new ProjectExcelReader.ExcelRow(row.rowNumber(), nameReviews.source(row));
            var key = duplicateKey(source, locations);
            List<String> oldErrors;
            try {
                oldErrors = json.readValue(row.errors(), new TypeReference<>() {});
            } catch (JsonProcessingException error) {
                throw new IllegalStateException(error);
            }
            var errors =
                    new ArrayList<>(
                            oldErrors.stream()
                                    .filter(error -> !error.startsWith("重复项目：仅保留"))
                                    .toList());
            if (key != null && !seen.add(key)) errors.add("重复项目：仅保留文件中首条同省、同地市、同名称记录");
            String payload = row.rowPayload();
            if (errors.isEmpty() && "FAILED".equals(row.status())) {
                var candidate = normalize(source, actor, errors);
                if (candidate != null) payload = stringify(candidate);
            }
            row.refreshValidation(payload, key, stringify(errors), errors.isEmpty());
        }
    }

    public static String normalized(String raw) {
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String safeFileName(String name) {
        if (name == null) return "unnamed.xlsx";
        var normalized = name.replaceAll("[\\r\\n/\\\\]", "_");
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ImportBatchSummary summary(
            ProjectImportBatchEntity batch, List<ProjectImportRowEntity> items) {
        var visibleReviews = nameReviews.visibleReports(batch.actorId(), items);
        int skipped = (int) items.stream().filter(row -> "SKIPPED".equals(row.status())).count();
        return new ImportBatchSummary(
                batch.id(),
                batch.status(),
                batch.totalRows(),
                batch.validRows(),
                batch.failedRows(),
                skipped,
                batch.importedRows(),
                batch.confirmedAt(),
                items.stream()
                        .map(
                                row ->
                                        new ImportBatchSummary.Row(
                                                row.rowNumber(),
                                                row.status(),
                                                row.errors(),
                                                row.projectId(),
                                                row.sourcePayload(),
                                                row.revisionNo(),
                                                visibleReviews.get(row.id())))
                        .toList());
    }
}
