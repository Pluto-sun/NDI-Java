package com.pluto.ndi.identity.service;

import java.util.Set;
import java.util.UUID;

/** 待办范围按审批职责区分，不能用全国查看权限替代审批权限。 */
public record ProjectApprovalScope(Set<UUID> interfaceProvinceIds, Set<UUID> provinceLeaderProvinceIds) {
    public ProjectApprovalScope {
        interfaceProvinceIds = Set.copyOf(interfaceProvinceIds);
        provinceLeaderProvinceIds = Set.copyOf(provinceLeaderProvinceIds);
    }
    public static ProjectApprovalScope none() { return new ProjectApprovalScope(Set.of(), Set.of()); }
    public boolean empty() { return interfaceProvinceIds.isEmpty() && provinceLeaderProvinceIds.isEmpty(); }
}
