package com.pluto.ndi.identity.service;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;

/** 省分按完整集合授权；组织编号仅保留单省／地市兼容值，多省时为空。 */
public record ProjectDataScope(Level level, UUID organizationId, Set<UUID> provinceIds) {

    public ProjectDataScope(Level level, UUID organizationId) { this(level, organizationId, Set.of()); }

    public ProjectDataScope {
        Objects.requireNonNull(level, "项目数据范围层级不得为空");
        provinceIds = Set.copyOf(provinceIds);
        if (level == Level.PROVINCE) {
            if (provinceIds.isEmpty() && organizationId != null) provinceIds = Set.of(organizationId);
            if (provinceIds.isEmpty() || organizationId != null && !provinceIds.contains(organizationId)) {
                throw new IllegalArgumentException("省分范围必须指定完整的省分集合");
            }
            organizationId = provinceIds.size() == 1 ? provinceIds.iterator().next() : null;
        } else if (!provinceIds.isEmpty() || (level == Level.NATIONAL) != (organizationId == null)) {
            throw new IllegalArgumentException("全国范围不得绑定组织编号，地市范围必须指定单个地市编号");
        }
    }

    public static ProjectDataScope provinces(Set<UUID> ids) { return new ProjectDataScope(Level.PROVINCE, null, ids); }

    /** 数据范围的组织层级。 */
    public enum Level {
        NATIONAL,
        PROVINCE,
        CITY
    }
}
