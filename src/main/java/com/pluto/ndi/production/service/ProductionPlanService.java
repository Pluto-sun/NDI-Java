package com.pluto.ndi.production.service;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.production.ProductionRecordDeletion;

import com.pluto.ndi.organization.service.*;
import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.production.mapper.ProductionPlanMapper;
import com.pluto.ndi.production.util.ProductionRules;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional
public class ProductionPlanService implements ProductionRecordDeletion {
    private final ProductionPlanMapper mapper;
    private final ProductionAccessService access;
    private final OrganizationDirectory organizations;
    private final AuditWriter audit;
    private final Clock clock;

    public ProductionPlanService(
            ProductionPlanMapper mapper,
            ProductionAccessService access,
            OrganizationDirectory organizations,
            AuditWriter audit,
            Clock clock) {
        this.mapper = mapper;
        this.access = access;
        this.organizations = organizations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProductionPlanMapper.Result list(UUID actor, ProductionPlanMapper.Filter filter) {
        if (filter.page() < 0
                || filter.size() < 1
                || filter.size() > 100
                || filter.search() != null && filter.search().length() > 300)
            throw new BusinessException(ProductionError.invalid("分页或查询条件无效"));
        if (filter.month() != null && !filter.month().isBlank())
            try {
                YearMonth.parse(filter.month());
            } catch (Exception e) {
                throw new BusinessException(ProductionError.invalid("月份无效"));
            }
        return mapper.list(access.require(actor), filter);
    }

    @Transactional(readOnly = true)
    public DashboardSummary dashboard(UUID actor, String from, String to) {
        return mapper.dashboard(access.require(actor), DashboardSummary.MonthRange.parse(from, to));
    }

    public record Reference(
            List<ProvinceOption> provinces, List<CityOption> cities, boolean writable) {}

    @Transactional(readOnly = true)
    public Reference reference(UUID actor) {
        var a = access.require(actor);
        var cities =
                organizations.findEnabledCities().stream()
                        .filter(c -> a.contains(c.provinceId(), c.id()))
                        .toList();
        var provinceIds =
                cities.stream()
                        .map(CityOption::provinceId)
                        .collect(java.util.stream.Collectors.toSet());
        return new Reference(
                organizations.findEnabledProvinces().stream()
                        .filter(p -> provinceIds.contains(p.id()))
                        .toList(),
                cities,
                a.writable());
    }

    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public ProductionPlan.Command validate(UUID actor, ProductionPlan.Command input) {
        var c = ProductionRules.validate(input);
        access.maintain(actor, c.provinceId(), c.cityId());
        if (organizations.findEnabledLocation(c.provinceId(), c.cityId()).isEmpty())
            throw new BusinessException(ProductionError.invalid("省份或地市未启用，或地市不属于所选省份"));
        return c;
    }

    public boolean duplicate(ProductionPlan.Command c) {
        return mapper.duplicate(c, new UUID(0, 0));
    }

    Set<ProductionPlan.Command> existingDuplicates(List<ProductionPlan.Command> commands) {
        return mapper.existingDuplicates(commands);
    }

    UUID createImported(UUID actor, ProductionPlan.Command validated) {
        var id = UUID.randomUUID();
        // 确认前已在当前导入事务中校验，数据库唯一约束继续防止并发重复。
        store(id, actor, validated, true);
        return id;
    }

    public ProductionPlan create(UUID actor, ProductionPlan.Command input) {
        var c = validate(actor, input);
        var id = UUID.randomUUID();
        write(id, actor, c, true);
        return mapper.find(id, false).orElseThrow();
    }

    public ProductionPlan update(UUID actor, UUID id, long version, ProductionPlan.Command input) {
        var old = locked(actor, id, version);
        var c = validate(actor, input);
        write(id, actor, c, false);
        return mapper.find(id, false).orElseThrow();
    }

    @Override
    public void delete(UUID actor, UUID id, long version) {
        var old = locked(actor, id, version);
        mapper.delete(id, Instant.now(clock));
        audit.record(
                actor,
                "PRODUCTION_PLAN",
                id,
                "PRODUCTION_PLAN_DELETED",
                Map.of("version", old.version(), "projectName", old.projectName()));
    }

    @Transactional(readOnly = true)
    public ProductionPlan detail(UUID actor, UUID id) {
        var permission = access.require(actor);
        var plan = mapper.find(id, false, permission.writable())
                .orElseThrow(() -> new BusinessException(ProductionError.notFound()));
        if (!permission.contains(plan.provinceId(), plan.cityId()))
            throw new BusinessException(ProductionError.forbidden());
        return plan;
    }

    private ProductionPlan locked(UUID actor, UUID id, long version) {
        var old =
                mapper.find(id, true)
                        .orElseThrow(() -> new BusinessException(ProductionError.notFound()));
        access.maintain(actor, old.provinceId(), old.cityId());
        if (old.version() != version) throw new BusinessException(ProductionError.conflict());
        return old;
    }

    private void write(UUID id, UUID actor, ProductionPlan.Command c, boolean create) {
        if (mapper.duplicate(c, id)) throw new BusinessException(ProductionError.conflict());
        store(id, actor, c, create);
    }

    private void store(UUID id, UUID actor, ProductionPlan.Command c, boolean create) {
        try {
            mapper.save(id, actor, c, create, Instant.now(clock));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ProductionError.conflict());
        }
        audit.record(
                actor,
                "PRODUCTION_PLAN",
                id,
                create ? "PRODUCTION_PLAN_CREATED" : "PRODUCTION_PLAN_UPDATED",
                Map.of(
                        "projectName",
                        c.projectName(),
                        "planMonth",
                        c.planMonth(),
                        "provinceId",
                        c.provinceId(),
                        "cityId",
                        c.cityId()));
    }
}
