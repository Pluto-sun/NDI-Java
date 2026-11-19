package com.pluto.ndi.organization.service;

import java.util.UUID;

/** 地市下拉选项。 */
public record CityOption(UUID id, UUID provinceId, String name, boolean legacyMunicipalityCity) {
    public CityOption(UUID id, UUID provinceId, String name) {
        this(id, provinceId, name, false);
    }
}
