package com.pluto.ndi.revenue.service;

import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.revenue.mapper.RevenueOrganizationMapper;
import com.pluto.ndi.revenue.mapper.RevenueOrganizationMapper.Assignment;
import com.pluto.ndi.revenue.util.RevenueFields;
import com.pluto.ndi.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevenueOrganizationService {
    private final RevenueAccessService access;
    private final RevenueOrganizationMapper mapper;
    private final OrganizationDirectory organizations;
    private final AuditWriter audit;
    private final Clock clock;

    public RevenueOrganizationService(RevenueAccessService access, RevenueOrganizationMapper mapper,
            OrganizationDirectory organizations, AuditWriter audit, Clock clock) {
        this.access = access;
        this.mapper = mapper;
        this.organizations = organizations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Assignment> list(UUID actor) {
        requireAdministrator(actor);
        return mapper.list();
    }

    @Transactional
    public Assignment update(UUID actor, String key, UUID provinceId) {
        requireAdministrator(actor);
        if (!key.matches("[a-fA-F0-9]{64}")) throw RevenueFields.invalid("组织标识无效");
        var name = mapper.name(key);
        if (name == null) throw RevenueFields.invalid("计收台账中找不到该组织");
        if (provinceId != null && organizations.findEnabledProvinces().stream()
                .noneMatch(province -> province.id().equals(provinceId)))
            throw RevenueFields.invalid("请选择已启用的省份");
        var previous = mapper.lock(key);
        if (!Objects.equals(previous, provinceId)) {
            mapper.update(key, actor, provinceId, Instant.now(clock));
            var detail = new LinkedHashMap<String, Object>();
            detail.put("organizationKey", key);
            detail.put("organizationName", name);
            detail.put("previousProvinceId", previous);
            detail.put("provinceId", provinceId);
            audit.record(actor, "REVENUE_ORGANIZATION", null, "REVENUE_ORGANIZATION_PROVINCE_CHANGED", detail);
        }
        return new Assignment(key.toUpperCase(java.util.Locale.ROOT), name, provinceId);
    }

    private void requireAdministrator(UUID actor) {
        if (!access.require(actor).administrator()) throw RevenueAccessService.forbidden();
    }
}
