package com.pluto.ndi.organization.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_province")
public class ProvinceEntity {

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

    public static ProvinceEntity create(String name) {
        var province = new ProvinceEntity();
        province.id = UUID.randomUUID();
        province.provinceCode = province.id.toString().replace("-", "");
        province.provinceName = name;
        province.enabled = true;
        return province;
    }

    public UUID id() { return id; }
    public boolean enabled() { return enabled; }
    public String provinceName() { return provinceName; }
}
