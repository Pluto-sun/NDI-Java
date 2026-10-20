package com.pluto.ndi.identity.service;

import java.util.List;
import java.util.UUID;

/** 供项目模块展示的最小账号视图，不暴露账号持久化对象。 */
public record ProjectUser(
        UUID id,
        String displayName,
        UUID provinceId,
        UUID cityId,
        List<String> roleCodes
) {
    public ProjectUser {
        roleCodes = List.copyOf(roleCodes);
    }
}
