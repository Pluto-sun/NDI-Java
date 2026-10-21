package com.pluto.ndi.identity.service;

import java.util.List;
import java.util.UUID;

/** 个人资料响应不包含密码摘要及会话撤销版本。 */
public record IdentityUserView(UUID id, String loginName, String displayName, String email,
        UUID provinceId, UUID cityId, List<String> roleCodes) {
    public IdentityUserView { roleCodes = List.copyOf(roleCodes); }
}
