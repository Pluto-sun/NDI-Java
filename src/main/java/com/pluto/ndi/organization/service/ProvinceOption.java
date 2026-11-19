package com.pluto.ndi.organization.service;

import java.util.UUID;

/** 省分下拉选项。 */
public record ProvinceOption(UUID id, String name, boolean municipality) {
    public ProvinceOption(UUID id, String name) {
        this(id, name, false);
    }
}
