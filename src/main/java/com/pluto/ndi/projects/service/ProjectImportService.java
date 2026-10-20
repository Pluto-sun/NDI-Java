package com.pluto.ndi.projects.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectImportBatchEntity;
import com.pluto.ndi.projects.domain.entity.ProjectImportRowEntity;
import com.pluto.ndi.projects.repository.ProjectImportBatchRepository;
import com.pluto.ndi.projects.repository.ProjectImportRowRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.util.ProjectExcelReader;
import com.pluto.ndi.projects.util.ProjectNameMatcher;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Excel 导入预检及人工确认；失败行不阻断其他行写入。 */
@Service
public class ProjectImportService {
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "网络化", "NETWORK", "数字化(格物)", "GEWU", "数字化(大数据)", "BIG_DATA",
            "智能化", "WANGWU", "智能化(万悟)", "WANGWU");
    private static final Map<String, String> STAGE_LABELS = Map.of(
            "方案交流", "SOLUTION_EXCHANGE", "招投标", "BIDDING", "签约", "SIGNED",
            "计收", "COLLECTION", "已终止", "TERMINATED");
    private static final Map<String, String> AMOUNT_HEADERS = Map.of(
            "NETWORK", "网络化预计金额（万）", "GEWU", "格物预计金额（万）",
            "BIG_DATA", "大数据预计金额（万）", "WANGWU", "万悟预计金额（万）");

    private final ProjectExcelReader reader;
    private final IdentityDirectory identity;
    private final OrganizationDirectory organizations;
    private final ProjectIndustryReferenceRepository industries;
    private final ProjectRepository projects;
    private final ProjectImportBatchRepository batches;
    private final ProjectImportRowRepository rows;
    private final ProjectImportWriter writer;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ProjectImportService(ProjectExcelReader reader, IdentityDirectory identity,
            OrganizationDirectory organizations, ProjectIndustryReferenceRepository industries,
            ProjectRepository projects, ProjectImportBatchRepository batches,
            ProjectImportRowRepository rows, ProjectImportWriter writer, ObjectMapper json,
            TransactionTemplate transactions, Clock clock) {
        this.reader = reader;
        this.identity = identity;
        this.organizations = organizations;
        this.industries = industries;
        this.projects = projects;
        this.batches = batches;
        this.rows = rows;
        this.writer = writer;
        this.json = json;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public ImportBatchSummary preview(UUID actorId, String fileName, byte[] bytes) {
        var actor = identity.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        List<ProjectExcelReader.ExcelRow> source;
        try {
            source = reader.read(bytes);
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        }
        if (source.isEmpty()) throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        var output = new ArrayList<ProjectImportRowEntity>();
        Set<String> encountered = new HashSet<>();
        Map<UUID, Set<String>> existingNames = new HashMap<>();
        for (var row : source) {
            var errors = new ArrayList<String>();
            String key = duplicateKey(row);
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
            if (candidate != null && existsName(existingNames, candidate.cityId(), row.get("项目名称"))) {
                errors.add("项目已存在于目标库");
            }
            output.add(ProjectImportRowEntity.preview(null, row.number(), key,
                    stringify(candidate == null ? row.cells() : candidate), stringify(errors), errors.isEmpty()));
        }
        int valid = (int) output.stream().filter(row -> "READY".equals(row.status())).count();
        var batch = ProjectImportBatchEntity.preview(actorId, safeFileName(fileName), sha256(bytes),
                output.size(), valid, Instant.now(clock));
        batches.save(batch);
        var saved = new ArrayList<ProjectImportRowEntity>();
        for (int i = 0; i < output.size(); i++) {
            var item = output.get(i);
            var raw = source.get(i);
            saved.add(ProjectImportRowEntity.preview(batch.id(), item.rowNumber(), duplicateKey(raw),
                    item.rowPayload(), item.errors(), "READY".equals(item.status())));
        }
        rows.saveAll(saved);
        return summary(batch, saved);
    }

    @Transactional(readOnly = true)
    public ImportBatchSummary find(UUID actorId, UUID batchId) {
        var batch = batches.findById(batchId).filter(item -> item.actorId().equals(actorId))
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        return summary(batch, rows.findByBatchIdOrderByRowNumber(batchId));
    }

    @Transactional(readOnly = true)
    public Page<ImportBatchListItem> findMine(UUID actorId, Pageable pageable) {
        identity.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return batches.findByActorIdOrderByCreatedAtDescIdDesc(actorId, pageable)
                .map(ImportBatchListItem::from);
    }

    @Transactional
    public ImportBatchSummary cancel(UUID actorId, UUID batchId) {
        var batch = batches.findByIdAndActorId(batchId, actorId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
        if (!"PREVIEWED".equals(batch.status())) {
            throw new BusinessException(ProjectErrorCode.IMPORT_NOT_PREVIEWED);
        }
        batch.cancel();
        return summary(batch, rows.findByBatchIdOrderByRowNumber(batchId));
    }

    public ImportBatchSummary confirm(UUID actorId, UUID batchId) {
        return confirm(actorId, batchId, null);
    }

    public ImportBatchSummary confirm(UUID actorId, UUID batchId, List<Integer> selectedRowNumbers) {
        var batch = transactions.execute(status -> {
            var locked = batches.findByIdAndActorId(batchId, actorId)
                    .orElseThrow(() -> new BusinessException(ProjectErrorCode.IMPORT_NOT_FOUND));
            if (!"PREVIEWED".equals(locked.status())) {
                if ("CANCELLED".equals(locked.status())) {
                    throw new BusinessException(ProjectErrorCode.IMPORT_NOT_PREVIEWED);
                }
                throw new BusinessException(ProjectErrorCode.IMPORT_ALREADY_CONFIRMED);
            }
            var batchRows = rows.findByBatchIdOrderByRowNumber(batchId);
            var ready = batchRows.stream().filter(row -> "READY".equals(row.status()))
                    .map(ProjectImportRowEntity::rowNumber).collect(java.util.stream.Collectors.toSet());
            var selected = selectedRowNumbers == null ? ready : new HashSet<>(selectedRowNumbers);
            if (selected.isEmpty() || selectedRowNumbers != null
                    && (selected.size() != selectedRowNumbers.size() || !ready.containsAll(selected))) {
                throw new BusinessException(ProjectErrorCode.IMPORT_SELECTION_INVALID);
            }
            batchRows.stream().filter(row -> "READY".equals(row.status()) && !selected.contains(row.rowNumber()))
                    .forEach(ProjectImportRowEntity::skip);
            locked.beginConfirmation(Instant.now(clock));
            return locked;
        });
        var sourceRows = rows.findByBatchIdOrderByRowNumber(batchId);
        int imported = 0;
        for (var row : sourceRows) {
            if (!"READY".equals(row.status())) continue;
            try {
                transactions.executeWithoutResult(status -> writer.importRow(actorId, row));
                imported++;
            } catch (RuntimeException exception) {
                String reason = exception instanceof IllegalStateException && exception.getMessage() != null
                        ? exception.getMessage() : "确认写入失败，请核查该行的目标约束";
                transactions.executeWithoutResult(status -> rows.findById(row.id()).orElseThrow()
                        .fail(stringify(List.of(reason))));
            }
        }
        int count = imported;
        transactions.executeWithoutResult(status -> {
            int skipped = (int) rows.findByBatchIdOrderByRowNumber(batchId).stream()
                    .filter(row -> "SKIPPED".equals(row.status())).count();
            batches.findById(batch.id()).orElseThrow().finish(count, skipped);
        });
        return find(actorId, batchId);
    }

    private ProjectImportCandidate normalize(ProjectExcelReader.ExcelRow row, ProjectActor actor, List<String> errors) {
        required(row, errors, "省分", "地市", "客户名称", "客户所属行业", "项目名称",
                "省分负责人", "地市客户经理", "项目建设内容", "三化类别", "项目阶段");
        var location = organizations.findEnabledLocationByNames(row.get("省分"), row.get("地市"));
        if (location.isEmpty()) errors.add("省分或地市未在新系统启用");
        else if (!identity.canImportProject(actor, location.get().provinceId(), location.get().cityId())) {
            errors.add("无此省市项目导入权限");
        }
        var industry = industries.findByIndustryNameAndEnabledTrue(row.get("客户所属行业"));
        if (industry.isEmpty()) errors.add("行业名称未匹配有效代码");
        var leader = location.flatMap(value -> identity.findEligibleProvinceLeaderByName(
                row.get("省分负责人"), value.provinceId()));
        if (location.isPresent() && leader.isEmpty()) errors.add("省分负责人未唯一匹配本省有效账号");
        var stage = STAGE_LABELS.get(row.get("项目阶段"));
        if (stage == null) errors.add("项目阶段未识别");
        if ("COLLECTION".equals(stage)) errors.add("计收阶段必须单独提供合同编号及签约金额，本模板暂不支持");
        var total = amount(row.get("项目总金额（万）"), "项目总金额", errors);
        var categories = new ArrayList<ProjectImportCandidate.CategoryAmount>();
        Set<String> codes = new HashSet<>();
        for (String label : row.get("三化类别").split("[,，、]")) {
            if (label.isBlank()) continue;
            String code = CATEGORY_LABELS.get(Normalizer.normalize(label.strip(), Normalizer.Form.NFKC));
            if (code == null) { errors.add("三化类别未识别：" + label); continue; }
            if (!codes.add(code)) { errors.add("三化类别重复：" + label); continue; }
            var categoryAmount = amount(row.get(AMOUNT_HEADERS.get(code)), label + "金额", errors);
            if (categoryAmount != null) categories.add(new ProjectImportCandidate.CategoryAmount(code, categoryAmount));
        }
        if (codes.isEmpty()) errors.add("至少选择一项三化类别");
        if (total != null && categories.stream().map(ProjectImportCandidate.CategoryAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(total) > 0) {
            errors.add("三化金额合计超过项目总金额");
        }
        if (!errors.isEmpty()) return null;
        var progress = ProjectExcelReader.progressColumns(row.cells()).stream()
                .map(item -> new ProjectImportCandidate.Progress(item.occurredAt(), item.content())).toList();
        return new ProjectImportCandidate(location.get().provinceId(), location.get().cityId(),
                row.get("项目名称"), row.get("客户名称"), industry.get().industryCode(),
                row.get("项目建设内容"), total, leader.get(), row.get("地市客户经理"),
                stage, List.copyOf(categories), progress, Instant.now(clock));
    }

    private void required(ProjectExcelReader.ExcelRow row, List<String> errors, String... headers) {
        for (var header : headers) if (row.get(header).isBlank()) errors.add(header + "不能为空");
    }

    private BigDecimal amount(String raw, String field, List<String> errors) {
        if (raw.isBlank()) { errors.add(field + "不能为空"); return null; }
        try {
            var number = new BigDecimal(raw.replace(",", ""));
            if (number.signum() < 0 || number.scale() > 2 || number.precision() - number.scale() > 16) {
                errors.add(field + "超出金额范围或小数超过两位"); return null;
            }
            return number;
        } catch (NumberFormatException exception) {
            errors.add(field + "不是有效金额"); return null;
        }
    }

    private boolean existsName(Map<UUID, Set<String>> cache, UUID cityId, String projectName) {
        return cache.computeIfAbsent(cityId, id -> projects.findByCityId(id).stream()
                .map(project -> ProjectNameMatcher.standard(project.projectName()))
                .collect(java.util.stream.Collectors.toSet())).contains(ProjectNameMatcher.standard(projectName));
    }

    private String duplicateKey(ProjectExcelReader.ExcelRow row) {
        if (row.get("省分").isBlank() || row.get("地市").isBlank() || row.get("项目名称").isBlank()) return null;
        return normalized(stripAdministrativeSuffix(row.get("省分"))) + ":"
                + normalized(stripAdministrativeSuffix(row.get("地市"))) + ":"
                + ProjectNameMatcher.standard(row.get("项目名称"));
    }

    private String stripAdministrativeSuffix(String name) {
        for (var suffix : new String[] {"自治区", "自治州", "地区", "省", "市"}) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }

    public static String normalized(String raw) {
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private String safeFileName(String name) {
        if (name == null) return "unnamed.xlsx";
        var normalized = name.replaceAll("[\\r\\n/\\\\]", "_");
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private ImportBatchSummary summary(ProjectImportBatchEntity batch, List<ProjectImportRowEntity> items) {
        int skipped = (int) items.stream().filter(row -> "SKIPPED".equals(row.status())).count();
        return new ImportBatchSummary(batch.id(), batch.status(), batch.totalRows(), batch.validRows(),
                batch.failedRows(), skipped, batch.importedRows(), items.stream().map(row ->
                new ImportBatchSummary.Row(row.rowNumber(), row.status(), row.errors(), row.projectId())).toList());
    }
}
