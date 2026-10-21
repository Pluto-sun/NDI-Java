package com.pluto.ndi.identity.service;

import java.util.List;
import java.util.UUID;

public record AdminAccountCommand(String loginName, String displayName, String email, List<String> roleCodes,
        UUID provinceId, UUID cityId) {
    public AdminAccountCommand { roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes); }
}
