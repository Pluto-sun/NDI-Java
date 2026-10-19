package com.pluto.ndi.organization.infrastructure;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_province")
class ProvinceEntity {

    @Id
    private UUID id;

    @Column(name = "province_code", nullable = false, unique = true)
    private String provinceCode;

    @Column(name = "province_name", nullable = false)
    private String provinceName;

    @Column(nullable = false)
    private boolean enabled;

    protected ProvinceEntity() {
    }
}
