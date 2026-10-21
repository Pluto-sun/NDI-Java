package com.pluto.ndi.identity.service;

import java.util.List;
import java.util.UUID;

/** 管理视图只报告是否已设置密码，不暴露密码摘要或会话凭据。 */
public record AdminAccountView(UUID id, String loginName, String displayName, String email, UUID provinceId,
        UUID cityId, boolean enabled, boolean passwordInitialized, long version, List<String> roleCodes) {
    public AdminAccountView { roleCodes = List.copyOf(roleCodes); }
}
