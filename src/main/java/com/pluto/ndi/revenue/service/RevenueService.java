package com.pluto.ndi.revenue.service;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.revenue.RevenueRecordDeletion;

import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.organization.service.*;
import java.math.BigDecimal;
import com.pluto.ndi.revenue.domain.*;
import com.pluto.ndi.revenue.domain.Revenue.*;
import com.pluto.ndi.revenue.mapper.RevenueMapper;
import com.pluto.ndi.revenue.util.RevenueFields;
import com.pluto.ndi.revenue.util.RevenueSourceOptions;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
@Transactional
public class RevenueService implements RevenueRecordDeletion {
    private final RevenueMapper mapper;
    private final RevenueAccessService access;
    private final RevenueFields fields;
    private final OrganizationDirectory organizations;
    private final IdentityDirectory identities;
    private final AuditWriter audit;
    private final Clock clock;

    public RevenueService(
            RevenueMapper mapper,
            RevenueAccessService access,
            RevenueFields fields,
            OrganizationDirectory organizations,
            IdentityDirectory identities,
            AuditWriter audit,
            Clock clock) {
        this.mapper = mapper;
        this.access = access;
        this.fields = fields;
        this.organizations = organizations;
        this.identities = identities;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummary dashboard(UUID actor, String from, String to) {
        return mapper.dashboard(access.require(actor), DashboardSummary.MonthRange.parse(from, to));
    }

    public record Reference(
            List<ProvinceOption> provinces,
            List<CityOption> cities,
            List<String> fields,
            List<String> required,
            boolean canCreate,
            Map<String, String> fixedOrganizations) {
        @com.fasterxml.jackson.annotation.JsonProperty
        public List<String> integratedOrganizations() {
            return Revenue.INTEGRATED_ORGANIZATIONS;
        }
    }

    @Transactional(readOnly = true)
    public Reference reference(UUID actor) {
        return new Reference(
                organizations.findEnabledProvinces(),
                organizations.findEnabledCities(),
                RevenueFields.FIELDS,
                RevenueFields.FIELDS.stream().filter(RevenueFields.REQUIRED::contains).toList(),
                access.require(actor).creator(),
                RevenueSourceOptions.FIXED_ORGANIZATIONS);
    }

    @Transactional(readOnly = true)
    public RevenueMapper.Listing list(UUID actor, RevenueMapper.Filter f) {
        if (f.page() < 0
                || f.page() > 100000
                || f.size() < 1
                || f.size() > 100
                || f.search() != null && f.search().length() > 300
                || f.organization() != null && f.organization().length() > 300) throw invalid("查询条件无效");
        if (f.category() != null && !f.category().isEmpty() && !Revenue.CATEGORIES.contains(f.category()))
            throw invalid("三化分类筛选无效");
        if (f.month() != null && !f.month().isBlank()) RevenueFields.month(f.month());
        if (f.state() != null
                && !Set.of("", "UNRECOGNIZED", "PENDING", "RECOGNIZED", "CHANGE_PENDING")
                        .contains(f.state())) throw invalid("办理状态无效");
        if (f.province() != null
                && !f.province().isBlank()
                && !Set.of("ORGANIZATION", "DS_TECH", "UNASSIGNED").contains(f.province()))
            try {
                UUID.fromString(f.province());
            } catch (IllegalArgumentException e) {
                throw invalid("省份筛选无效");
            }
        return mapper.list(access.require(actor), f);
    }

    @Transactional(readOnly = true)
    public Entry detail(UUID actor, UUID id) {
        return require(actor, id, false);
    }

    private Entry require(UUID actor, UUID id, boolean lock) {
        var a = access.require(actor);
        var e =
                mapper.find(id, a, lock)
                        .orElseThrow(() -> new BusinessException(RevenueError.notFound()));
        if (!a.readable(e.provinceId(), e.specificOrganization()) && !mapper.readsAssignedOrganization(id, a))
            throw RevenueAccessService.forbidden();
        return e;
    }

    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public RevenueFields.Source validate(UUID actor, Map<String, String> values) {
        access.create(actor);
        return fields.normalize(values);
    }

    public Set<RevenueFields.Source> existingDuplicates(List<RevenueFields.Source> sources) {
        return mapper.existingDuplicates(sources);
    }

    public boolean duplicate(RevenueFields.Source source) {
        return !existingDuplicates(List.of(source)).isEmpty();
    }

    public Entry create(UUID actor, Map<String, String> values) {
        var source = validate(actor, values);
        if (Revenue.NETWORK.equals(source.fields().get(Revenue.CATEGORY_FIELD)))
            throw invalid("网络化条目仅允许系统管理员通过 Excel 导入");
        if (duplicate(source)) throw conflict();
        return create(actor, source, null);
    }

    public record ValidatedImport(RevenueFields.Source source, Recognition recognition) {}

    // 预检会将业务异常记录为行错误，不能让该异常将整批事务标记为回滚。
    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public ValidatedImport validateImport(UUID actor, Map<String, String> values) {
        var sourceValues = new LinkedHashMap<>(values);
        sourceValues.keySet().removeAll(List.of(RevenueImport.AMOUNT, RevenueImport.PROJECT, RevenueImport.STATUS, RevenueImport.PROVINCE));
        var source = validate(actor, sourceValues);
        var network = Revenue.NETWORK.equals(source.fields().get(Revenue.CATEGORY_FIELD));
        if (network && !access.require(actor).administrator())
            throw new BusinessException(new RevenueError(org.springframework.http.HttpStatus.FORBIDDEN,
                    "REVENUE_NETWORK_IMPORT_FORBIDDEN", "网络化条目仅允许系统管理员导入"));
        var recognition = importRecognition(values, network);
        if (network && recognition == null)
            throw invalid("网络化条目须填写大于 0 的有效认定金额和对应省分（集成）");
        return new ValidatedImport(source, recognition);
    }

    public Entry importEntry(UUID actor, Map<String, String> values) {
        var validated = validateImport(actor, values);
        return create(actor, validated.source(), validated.recognition());
    }

    private Recognition importRecognition(Map<String, String> values, boolean network) {
        String amountText = importValue(values, RevenueImport.AMOUNT);
        String name = importValue(values, RevenueImport.PROJECT);
        String province = importValue(values, RevenueImport.PROVINCE);
        if (amountText.isEmpty() || province.isEmpty()) return null;
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.replace(",", ""));
        } catch (NumberFormatException failure) {
            return null;
        }
        if (amount.signum() <= 0) return null;
        String status = importValue(values, RevenueImport.STATUS);
        if (Revenue.INTEGRATED_ORGANIZATIONS.contains(province))
            return fields.recognition(new Recognition(amount, name, status, null, province), network);
        var integratedProvince = organizations.findEnabledProvinceByName(province)
                .or(() -> organizations.findEnabledProvinces().stream()
                        .filter(item -> item.id().toString().equals(province)).findFirst())
                .orElseThrow(() -> invalid("对应省分（集成）未匹配启用的省份、数科或数智"));
        return fields.recognition(new Recognition(amount, name, status, integratedProvince.id()), network);
    }

    private static String importValue(Map<String, String> values, String field) {
        String value = Objects.toString(values.get(field), "").strip();
        return value.equals("--") ? "" : value;
    }

    private Entry create(UUID actor, RevenueFields.Source source, Recognition recognition) {
        UUID id = UUID.randomUUID();
        try {
            mapper.insert(id, actor, source, Instant.now(clock));
        } catch (DataIntegrityViolationException e) {
            throw conflict();
        }
        audit.record(
                actor,
                "REVENUE_ENTRY",
                id,
                "REVENUE_CREATED",
                Map.of("month", source.month(), "source", source.fields()));
        var entry = require(actor, id, false);
        if (recognition == null) return entry;
        apply(actor, entry, recognition, null, Instant.now(clock));
        mapper.bump(id, Instant.now(clock));
        return require(actor, id, false);
    }

    @Override
    public void delete(UUID actor, UUID id, long version) {
        var entry = require(actor, id, true);
        if (entry.version() != version) throw conflict();
        if (entry.pendingRequestId() != null)
            throw new BusinessException(new RevenueError(org.springframework.http.HttpStatus.CONFLICT,
                    "REVENUE_DELETE_PENDING", "存在待审批的计收记录，请先处理审批"));
        deleteChecked(actor, entry);
    }

    @Override
    public boolean deleteUnlessPending(UUID actor, UUID id, long version) {
        var entry = require(actor, id, true);
        if (mapper.lockPendingRecognition(id)) return false;
        if (entry.version() != version) throw conflict();
        deleteChecked(actor, entry);
        return true;
    }

    private void deleteChecked(UUID actor, Entry entry) {
        mapper.delete(entry.id(), Instant.now(clock));
        audit.record(actor, "REVENUE_ENTRY", entry.id(), "REVENUE_ENTRY_DELETED", Map.of("version", entry.version()));
    }

    public record Approver(UUID id, String displayName) {}

    @Transactional(readOnly = true)
    public List<Approver> approvers(UUID actor, UUID id) {
        var entry = require(actor, id, false);
        requireRecognitionScope(actor, entry);
        return identities.findEnabledProjectUsers().stream()
                .filter(user -> !user.id().equals(actor) && user.roleCodes().contains("GROUP_REVIEWER"))
                .map(user -> new Approver(user.id(), user.displayName()))
                .toList();
    }

    private void requireRecognitionScope(UUID actor, Entry entry) {
        if (!access.require(actor).recognize(entry.provinceId(), entry.specificOrganization(),
                entry.source().get(Revenue.CATEGORY_FIELD)))
            throw RevenueAccessService.forbidden();
    }

    public Entry recognize(UUID actor, UUID id, long version, Recognition input, UUID approver) {
        var e = require(actor, id, true);
        requireRecognitionScope(actor, e);
        com.pluto.ndi.shared.approval.ApprovalWorkflow.requireNoPending(
                e.pendingRequestId() != null, RevenueService::conflict);
        if (!com.pluto.ndi.shared.approval.ApprovalWorkflow.sameVersion(e.version(), version))
            throw conflict();
        if (approver == null || approver.equals(actor) || identities.findProjectUser(approver)
                .filter(user -> user.roleCodes().contains("GROUP_REVIEWER")).isEmpty())
            throw invalid("请选择一名已启用的集团审核人，且不能选择本人");
        var value = fields.recognition(input);
        var at = Instant.now(clock);
        UUID request = UUID.randomUUID();
        mapper.submit(request, e, actor, approver, value, at);
        mapper.notify(approver, id, request, "SUBMITTED", "计收认定申请待审核", at);
        audit.record(actor, "REVENUE_REQUEST", request, "REVENUE_SUBMITTED",
                Map.of("entryId", id, "proposed", value, "approverId", approver));
        mapper.bump(id, at);
        return require(actor, id, false);
    }

    private void apply(UUID actor, Entry e, Recognition value, UUID request, Instant at) {
        mapper.recognize(e, value, actor, request, at);
        var change = new LinkedHashMap<String, Object>();
        change.put("before", e.recognition());
        change.put("after", value);
        change.put("entryId", e.id());
        audit.record(actor, "REVENUE_ENTRY", e.id(), "REVENUE_RECOGNIZED", change);
    }

    public Entry decide(
            UUID actor,
            UUID requestId,
            long entryVersion,
            long requestVersion,
            String decision,
            String opinion) {
        var initial = request(requestId, false);
        var e = require(actor, initial.entryId(), true);
        var r = request(requestId, true);
        var a = access.require(actor);
        com.pluto.ndi.shared.approval.ApprovalWorkflow.requirePending(
                r.status(), RevenueService::conflict);
        if (e.version() != entryVersion
                || r.version() != requestVersion
                || r.baseVersion() != e.version()) throw conflict();
        if (!Set.of("APPROVED", "REJECTED", "WITHDRAWN").contains(decision))
            throw invalid("审批动作无效");
        String text = Objects.toString(opinion, "").strip();
        if (text.length() > 2000) throw invalid("审核意见不能超过2000字");
        if (decision.equals("WITHDRAWN")) {
            if (!r.initiatorId().equals(actor)) throw RevenueAccessService.forbidden();
        } else {
            if (!a.groupApprover() || actor.equals(r.initiatorId()) || !actor.equals(r.approverId())
                    || !Revenue.REVIEWED_CATEGORIES.contains(e.source().getOrDefault(Revenue.CATEGORY_FIELD, "")))
                throw RevenueAccessService.forbidden();
            com.pluto.ndi.shared.approval.ApprovalWorkflow.requireOpinion(
                    !decision.equals("REJECTED"), text, () -> invalid("驳回必须填写审核意见"));
            if (decision.equals("APPROVED"))
                apply(
                        actor,
                        e,
                        fields.recognition(r.proposed()),
                        r.id(),
                        Instant.now(clock));
        }
        var at = Instant.now(clock);
        mapper.finish(r.id(), decision, text, at);
        mapper.bump(e.id(), at);
        UUID recipient = decision.equals("WITHDRAWN") ? r.approverId() : r.initiatorId();
        mapper.notify(
                recipient,
                e.id(),
                r.id(),
                decision,
                "计收认定申请"
                        + switch (decision) {
                            case "APPROVED" -> "已通过";
                            case "REJECTED" -> "已驳回";
                            default -> "已撤销";
                        },
                at);
        audit.record(
                actor,
                "REVENUE_REQUEST",
                r.id(),
                "REVENUE_" + decision,
                Map.of("entryId", e.id(), "opinion", text));
        return require(actor, e.id(), false);
    }

    private Request request(UUID id, boolean lock) {
        try {
            return mapper.request(id, lock);
        } catch (NoSuchElementException e) {
            throw new BusinessException(RevenueError.notFound());
        }
    }

    @Transactional(readOnly = true)
    public List<Request> requests(UUID actor, UUID id) {
        require(actor, id, false);
        return mapper.requests(id);
    }

    @Transactional(readOnly = true)
    public Page<Request> requestPage(UUID actor, String view, int page, int size) {
        if (!Set.of("mine", "history").contains(view)
                || page < 0
                || page > 100000
                || size < 1
                || size > 100) throw invalid("审批查询条件无效");
        return mapper.requestPage(access.require(actor), view, page, size);
    }

    @Transactional(readOnly = true)
    public List<History> history(UUID actor, UUID id) {
        require(actor, id, false);
        return mapper.history(id);
    }

    @Transactional(readOnly = true)
    public List<Request> pending(UUID actor) {
        var a = access.require(actor);
        return a.groupApprover() ? mapper.assigned(actor) : List.of();
    }

    @Transactional(readOnly = true)
    public List<Notice> notices(UUID actor) {
        var a = access.require(actor);
        return mapper.notices(actor).stream()
                .filter(
                        n ->
                                mapper.find(n.entryId(), a, false)
                                        .filter(
                                                e ->
                                                        a.readable(
                                                                e.provinceId(),
                                                                e.specificOrganization()))
                                        .isPresent())
                .toList();
    }

    @Transactional(readOnly = true)
    public RevenueMapper.Counts counts(UUID actor) {
        return mapper.counts(access.require(actor));
    }

    public void read(UUID actor, UUID notice) {
        access.require(actor);
        mapper.read(actor, notice, Instant.now(clock));
    }

    private static BusinessException invalid(String text) {
        return RevenueFields.invalid(text);
    }

    private static BusinessException conflict() {
        return new BusinessException(RevenueError.conflict());
    }
}
