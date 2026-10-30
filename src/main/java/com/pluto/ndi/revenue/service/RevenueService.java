package com.pluto.ndi.revenue.service;

import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.organization.service.*;
import com.pluto.ndi.revenue.domain.*;
import com.pluto.ndi.revenue.domain.Revenue.*;
import com.pluto.ndi.revenue.mapper.RevenueMapper;
import com.pluto.ndi.revenue.util.RevenueFields;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
@Transactional
public class RevenueService {
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

    public record Reference(
            List<ProvinceOption> provinces,
            List<String> fields,
            List<String> required,
            boolean canCreate) {}

    @Transactional(readOnly = true)
    public Reference reference(UUID actor) {
        return new Reference(
                organizations.findEnabledProvinces(),
                RevenueFields.FIELDS,
                RevenueFields.FIELDS.stream().filter(RevenueFields.REQUIRED::contains).toList(),
                access.require(actor).creator());
    }

    @Transactional(readOnly = true)
    public RevenueMapper.Listing list(UUID actor, RevenueMapper.Filter f) {
        if (f.page() < 0
                || f.page() > 100000
                || f.size() < 1
                || f.size() > 100
                || f.search() != null && f.search().length() > 300) throw invalid("查询条件无效");
        if (f.month() != null && !f.month().isBlank()) RevenueFields.month(f.month());
        if (f.state() != null
                && !Set.of("", "UNRECOGNIZED", "PENDING", "RECOGNIZED", "CHANGE_PENDING")
                        .contains(f.state())) throw invalid("办理状态无效");
        if (f.province() != null && !f.province().isBlank() && !f.province().equals("ORGANIZATION"))
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
        if (!a.readable(e.provinceId(), e.specificOrganization()))
            throw RevenueAccessService.forbidden();
        return e;
    }

    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public RevenueFields.Source validate(UUID actor, Map<String, String> values) {
        access.create(actor);
        return fields.normalize(values);
    }

    public boolean duplicate(RevenueFields.Source source) {
        return mapper.duplicate(source);
    }

    public Entry create(UUID actor, Map<String, String> values) {
        var source = validate(actor, values);
        if (duplicate(source)) throw conflict();
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
        return require(actor, id, false);
    }

    public Entry recognize(UUID actor, UUID id, long version, Recognition input) {
        var e = require(actor, id, true);
        var a = access.require(actor);
        if (!a.recognize(e.provinceId(), e.specificOrganization()))
            throw RevenueAccessService.forbidden();
        com.pluto.ndi.shared.approval.ApprovalWorkflow.requireNoPending(
                e.pendingRequestId() != null, RevenueService::conflict);
        if (!com.pluto.ndi.shared.approval.ApprovalWorkflow.sameVersion(e.version(), version))
            throw conflict();
        var value = fields.recognition(input, e.clauseAmount());
        var at = Instant.now(clock);
        if (a.reviewer()) {
            apply(actor, e, value, null, at);
        } else {
            var targets = identities.findEligibleStageApprovers(e.provinceId());
            if (targets.size() != 1) throw invalid("本省尚未配置有效的唯一接口人，请联系管理员配置");
            UUID approver = targets.getFirst().id();
            if (approver.equals(actor)) throw RevenueAccessService.forbidden();
            UUID request = UUID.randomUUID();
            mapper.submit(request, e, actor, approver, value, at);
            mapper.notify(approver, id, request, "SUBMITTED", "计收认定申请待审核", at);
            audit.record(
                    actor,
                    "REVENUE_REQUEST",
                    request,
                    "REVENUE_SUBMITTED",
                    Map.of("entryId", id, "proposed", value, "approverId", approver));
        }
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
            if (!a.reviewer()
                    || actor.equals(r.initiatorId())
                    || !actor.equals(r.approverId())
                    || !a.recognize(e.provinceId(), e.specificOrganization())
                    || !identities.isEligibleStageApprover(actor, e.provinceId()))
                throw RevenueAccessService.forbidden();
            com.pluto.ndi.shared.approval.ApprovalWorkflow.requireOpinion(
                    !decision.equals("REJECTED"), text, () -> invalid("驳回必须填写审核意见"));
            if (decision.equals("APPROVED"))
                apply(
                        actor,
                        e,
                        fields.recognition(r.proposed(), e.clauseAmount()),
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
        if (!a.reviewer()) return List.of();
        return mapper.assigned(actor).stream()
                .filter(
                        r -> {
                            var e = require(actor, r.entryId(), false);
                            return a.recognize(e.provinceId(), e.specificOrganization())
                                    && identities.isEligibleStageApprover(actor, e.provinceId());
                        })
                .toList();
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

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void handover(ReviewerScopeChanged event) {
        for (var province : event.provinceIds().stream().sorted().toList()) {
            var targets = identities.findEligibleStageApprovers(province);
            if (targets.size() != 1) continue;
            UUID target = targets.getFirst().id();
            for (var id : mapper.pendingEntries(province)) {
                var a = access.require(target);
                var e = mapper.find(id, a, true).orElseThrow();
                if (e.pendingRequestId() == null) continue;
                var r = request(e.pendingRequestId(), true);
                if (!r.status().equals("PENDING") || r.approverId().equals(target)) continue;
                if (r.initiatorId().equals(target)) throw invalid("计收待审申请不能交接给发起人本人");
                mapper.reassign(r.id(), target);
                var at = Instant.now(clock);
                mapper.notify(target, e.id(), r.id(), "REASSIGNED", "接口人交接：计收认定申请待审核", at);
                mapper.notify(r.approverId(), e.id(), r.id(), "TRANSFERRED", "计收认定待办已交接给新接口人", at);
                audit.record(
                        event.actorId(),
                        "REVENUE_REQUEST",
                        r.id(),
                        "REVENUE_REASSIGNED",
                        Map.of(
                                "previousApproverId",
                                r.approverId(),
                                "approverId",
                                target,
                                "provinceId",
                                province));
            }
        }
    }

    private static BusinessException invalid(String text) {
        return RevenueFields.invalid(text);
    }

    private static BusinessException conflict() {
        return new BusinessException(RevenueError.conflict());
    }
}
