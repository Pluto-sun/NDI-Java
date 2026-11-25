package com.pluto.ndi.projects.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.repository.ProjectMonthlySnapshotRepository;
import com.pluto.ndi.projects.repository.ProjectMonthlySnapshotRepository.Snapshot;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.imports.*;
import com.pluto.ndi.shared.imports.ImportModels.Rule;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class HistoricalSnapshotImport extends TabularImportEngine<Snapshot> {
    private static final String MONTH = "快照月份", CODE = "系统项目编号", STAGE = "月末阶段",
            PROVINCE = "月末归属省分", CITY = "地市", INDUSTRY = "月末客户所属行业",
            TOTAL = "月末项目总金额", REVISION = "确认覆盖的修订号";
    private static final List<String> PRODUCTS = Arrays.stream(ProjectCategory.values())
            .map(product -> product.label() + "月末金额").toList();
    private static final List<String> REQUIRED = java.util.stream.Stream.concat(
            List.of(MONTH, CODE, STAGE, PROVINCE, CITY, TOTAL).stream(), PRODUCTS.stream()).toList();
    private final JdbcTemplate jdbc;
    private final OrganizationDirectory organizations;
    private final IdentityDirectory identities;
    private final ProjectMonthlySnapshotRepository snapshots;
    private final Clock clock;
    private final AuditWriter audit;

    public HistoricalSnapshotImport(NamedParameterJdbcTemplate named, JdbcTemplate jdbc,
            ImportWorkbookDirectory files, ObjectMapper json, Clock clock, AuditWriter audit,
            OrganizationDirectory organizations, IdentityDirectory identities,
            ProjectMonthlySnapshotRepository snapshots) {
        super(new ImportTaskStore(named, json, "snapshot"), files, json, clock, audit, "snapshot");
        this.jdbc = jdbc;
        this.organizations = organizations;
        this.identities = identities;
        this.snapshots = snapshots;
        this.clock = clock;
        this.audit = audit;
    }

    @Override
    public void authorize(UUID actor) {
        if (identities.findProjectUser(actor).filter(user -> user.roleCodes().contains("SYSTEM_ADMIN")).isEmpty())
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
    }

    @Override
    public List<String> fields() {
        return java.util.stream.Stream.concat(REQUIRED.stream(), List.of(INDUSTRY, REVISION).stream()).toList();
    }

    @Override
    protected List<Rule> rules() {
        return fields().stream().map(field -> new Rule(field,
                REQUIRED.contains(field) ? "REQUIRED" : "OPTIONAL",
                field.equals(REVISION) ? "仅在确认覆盖冲突时填写预检显示的修订号" : "")).toList();
    }

    @Override
    protected List<String> missing(Map<String, String> mapping) {
        return ImportWorkflow.missing(mapping, REQUIRED);
    }

    private YearMonth closedMonth(String text) {
        try {
            if (!text.matches("[0-9]{4}-[0-9]{2}")) throw invalid("快照月份须为 YYYY-MM");
            var month = YearMonth.parse(text);
            if (month.getYear() < 1000 || !month.isBefore(YearMonth.now(clock.withZone(ZoneId.of("Asia/Shanghai")))))
                throw invalid("仅支持已结束月份");
            return month;
        } catch (DateTimeException exception) {
            throw invalid("快照月份无效");
        }
    }

    @Override
    protected Snapshot validate(UUID actor, Map<String, String> cells) {
        var month = closedMonth(text(cells, MONTH));
        var code = text(cells, CODE);
        var project = jdbc.query("SELECT BIN_TO_UUID(id) FROM project_project WHERE project_code=?",
                (row, index) -> UUID.fromString(row.getString(1)), code).stream().findFirst()
                .orElseThrow(() -> invalid("无法匹配系统项目编号：" + code));
        var stage = Arrays.stream(ProjectStage.values())
                .filter(item -> item.matchesImportValue(text(cells, STAGE)))
                .findFirst().orElseThrow(() -> invalid("月末阶段无效"));
        var location = organizations.findEnabledLocationByNames(text(cells, PROVINCE), text(cells, CITY))
                .orElseThrow(() -> invalid("月末省分和地市不匹配"));
        String industry = text(cells, INDUSTRY);
        String industryCode = industry.isEmpty() ? null : jdbc.query(
                "SELECT industry_code FROM project_industry_reference WHERE industry_name=?",
                (row, index) -> row.getString(1), industry).stream().findFirst()
                .orElseThrow(() -> invalid("月末客户所属行业未匹配字典"));
        var total = amount(cells, TOTAL);
        var products = PRODUCTS.stream().map(field -> amount(cells, field)).toList();
        if (products.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(total) > 0)
            throw invalid("产品金额合计不得超过月末项目总金额");
        var candidate = new Snapshot(project, month, location.provinceId(), location.cityId(),
                organizations.findCityNames(List.of(location.cityId())).get(location.cityId()),
                industryCode, industry.isEmpty() ? "未填写" : industry, stage.name(), total,
                new ProjectCategoryTotals(products.get(0), products.get(1), products.get(2), products.get(3)), 0);
        var existing = snapshots.find(project, month).orElse(null);
        if (existing != null && !same(existing, candidate)
                && !Integer.toString(existing.revisionNo()).equals(text(cells, REVISION)))
            throw invalid("已有快照与导入值冲突。确认覆盖时，请填写当前修订号 " + existing.revisionNo());
        return candidate;
    }

    private static String text(Map<String, String> cells, String field) {
        return cells.getOrDefault(field, "").strip();
    }

    private BigDecimal amount(Map<String, String> cells, String field) {
        try {
            String raw = text(cells, field).replace(",", "");
            if (!raw.matches("[0-9]{1,16}(\\.[0-9]{1,2})?")) throw invalid(field + "必填，须为非负金额且最多两位小数");
            return new BigDecimal(raw).setScale(2);
        } catch (NumberFormatException exception) {
            throw invalid(field + "金额无效");
        }
    }

    private boolean same(Snapshot a, Snapshot b) {
        return a.provinceId().equals(b.provinceId()) && a.cityId().equals(b.cityId())
                && Objects.equals(a.industryCode(), b.industryCode()) && a.stageCode().equals(b.stageCode())
                && a.totalAmount().compareTo(b.totalAmount()) == 0 && a.categories().equals(b.categories());
    }

    @Override
    protected Object duplicateKey(Snapshot value) { return List.of(value.month(), value.projectId()); }

    @Override
    protected boolean duplicate(Snapshot value) { return false; }

    @Override
    protected UUID create(UUID actor, Map<String, String> cells) {
        snapshots.lockControl();
        var value = validate(actor, cells);
        var before = snapshots.find(value.projectId(), value.month()).orElse(null);
        if (before != null && same(before, value)) return null;
        var now = Instant.now(clock);
        if (before == null) snapshots.insert(value, now);
        else snapshots.revise(before, value, new ProjectMonthlySnapshotRepository.Event(value.projectId(),
                value.month(), now, "HISTORICAL_IMPORT", UUID.randomUUID(), "管理员确认覆盖历史快照",
                false, null, null, null, null, null, null), now);
        var detail = new LinkedHashMap<String, Object>();
        detail.put("before", before);
        detail.put("after", value);
        audit.record(actor, "PROJECT_SNAPSHOT", value.projectId(), "HISTORICAL_SNAPSHOT_IMPORTED", detail);
        return value.projectId();
    }

    public void completeMonth(UUID actor, String monthText) {
        authorize(actor);
        var month = closedMonth(monthText);
        snapshots.lockControl();
        snapshots.markImportedComplete(month);
        audit.record(actor, "PROJECT_SNAPSHOT", actor, "HISTORICAL_SNAPSHOT_MONTH_COMPLETED", Map.of("month", month.toString()));
    }

    public List<Map<String, Object>> oldRevisions(UUID actor) {
        authorize(actor);
        return jdbc.queryForList("""
                SELECT p.project_code, r.month_start, BIN_TO_UUID(r.source_id) approval_id,
                       r.revision_no, a.submitted_at, a.decided_at,
                       r.before_stage_code, r.after_stage_code, r.before_total_amount, r.after_total_amount,
                       r.before_network_amount, r.before_gewu_amount, r.before_big_data_amount, r.before_wangwu_amount
                FROM project_monthly_snapshot_revision r
                JOIN project_approval_request a ON a.id=r.source_id
                JOIN project_project p ON p.id=r.project_id
                WHERE r.source_kind='APPROVAL'
                  AND r.month_start < DATE_FORMAT(DATE_ADD(a.decided_at, INTERVAL 8 HOUR), '%Y-%m-01')
                ORDER BY r.month_start, p.project_code, r.revision_no
                """);
    }

    @Override
    protected BusinessException invalid(String message) { return error(HttpStatus.BAD_REQUEST, message); }
    @Override
    protected BusinessException conflict() { return error(HttpStatus.CONFLICT, "导入任务已变化，请重新预检"); }
    private BusinessException error(HttpStatus status, String message) {
        return new BusinessException(new ErrorCode() {
            public HttpStatus status() { return status; }
            public String code() { return "HISTORICAL_SNAPSHOT_" + status.name(); }
            public String title() { return message; }
            public String message() { return message; }
        });
    }
}
