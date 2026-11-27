package com.pluto.ndi.administration.service;

import com.pluto.ndi.administration.domain.ListColumnCatalog;
import com.pluto.ndi.administration.mapper.ListColumnConfigurationMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.production.ProductionListAccess;
import com.pluto.ndi.revenue.RevenueListAccess;
import com.pluto.ndi.shared.security.SecurityErrorCode;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ListColumnConfigurationService {
    private final IdentityDirectory identity;
    private final ProductionListAccess production;
    private final RevenueListAccess revenue;
    private final ListColumnConfigurationMapper mapper;
    private final AuditWriter audit;

    public ListColumnConfigurationService(IdentityDirectory identity, ProductionListAccess production,
            RevenueListAccess revenue, ListColumnConfigurationMapper mapper, AuditWriter audit) {
        this.identity = identity;
        this.production = production;
        this.revenue = revenue;
        this.mapper = mapper;
        this.audit = audit;
    }

    public record Fixed(List<String> fixedFields) {}
    public record Configuration(ListColumnCatalog id, String title, List<ListColumnCatalog.Field> fields,
                                List<String> fixedFields) {}
    public record Catalog(int maxFixed, List<Configuration> lists) {}

    @Transactional(readOnly = true)
    public Fixed read(UUID actor, ListColumnCatalog list) {
        switch (list) {
            case REVENUE -> revenue.requireListRead(actor);
            case PRODUCTION -> production.requireListRead(actor);
            default -> identity.findProjectActor(actor)
                    .flatMap(identity::findReadableProjectScope)
                    .orElseThrow(() -> new BusinessException(SecurityErrorCode.FORBIDDEN));
        }
        // 现有各列表的业务列共享其行读取权限。字段下线后从结果中过滤。
        return new Fixed(availableFields(list));
    }

    @Transactional(readOnly = true)
    public Catalog manage(UUID actor) {
        requireAdmin(actor);
        return new Catalog(ListColumnCatalog.MAX_FIXED, Arrays.stream(ListColumnCatalog.values())
                .map(list -> new Configuration(list, list.title(), list.fields(), availableFields(list))).toList());
    }

    @Transactional
    public Fixed save(UUID actor, ListColumnCatalog list, List<String> fields) {
        requireAdmin(actor);
        if (fields == null || fields.size() > ListColumnCatalog.MAX_FIXED
                || new HashSet<>(fields).size() != fields.size() || !fields.stream().allMatch(list::contains))
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        var previous = mapper.lock(list);
        if (!previous.equals(fields)) {
            mapper.save(list, fields);
            var id = UUID.nameUUIDFromBytes(("list-columns:" + list.name()).getBytes(StandardCharsets.UTF_8));
            audit.record(actor, "LIST_COLUMN_CONFIGURATION", id, "LIST_FIXED_COLUMNS_UPDATED",
                    Map.of("listId", list.name(), "before", previous, "after", fields));
        }
        return new Fixed(fields);
    }

    private List<String> availableFields(ListColumnCatalog list) {
        return mapper.read(list).stream().filter(list::contains).distinct().limit(ListColumnCatalog.MAX_FIXED).toList();
    }

    private void requireAdmin(UUID actor) {
        if (identity.findProjectActor(actor).filter(user -> user.enabled()).isEmpty()
                || identity.findProjectUser(actor).filter(user -> user.roleCodes().contains("SYSTEM_ADMIN")).isEmpty())
            throw new BusinessException(SecurityErrorCode.FORBIDDEN);
    }
}
