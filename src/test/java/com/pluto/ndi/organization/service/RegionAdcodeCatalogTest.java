package com.pluto.ndi.organization.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RegionAdcodeCatalogTest {

    @Test
    void mapsOrganizationNamesToBoundaryCodes() {
        var catalog = new RegionAdcodeCatalog(new ObjectMapper());
        assertThat(catalog.province("广东")).isEqualTo("440000");
        assertThat(catalog.province("广东省")).isEqualTo("440000");
        assertThat(catalog.city("广东", "广州")).isEqualTo("440100");
        assertThat(catalog.city("广东省", "广州市")).isEqualTo("440100");
        assertThat(catalog.city("广西", "广州")).isNull();
    }
}
