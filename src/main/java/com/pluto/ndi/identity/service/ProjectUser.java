package com.pluto.ndi.identity.service;

import java.util.List;
import java.util.UUID;

/** 最小账号视图；接口人以负责省份集合授权，账号属地不作为接口人权限依据。 */
public record ProjectUser(
        UUID id,
        String displayName,
        UUID provinceId,
        UUID cityId,
        List<String> roleCodes,
        List<UUID> reviewerProvinceIds
) {
    public ProjectUser(UUID id, String displayName, UUID provinceId, UUID cityId, List<String> roleCodes) {
        this(id, displayName, provinceId, cityId, roleCodes, List.of());
    }
    public ProjectUser {
        roleCodes = List.copyOf(roleCodes);
        reviewerProvinceIds = List.copyOf(reviewerProvinceIds);
    }
}
