package com.pluto.ndi.organization.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organization_city")
public class CityEntity {

    @Id
    private UUID id;

    @Column(name = "province_id", nullable = false)
    private UUID provinceId;

    @Column(name = "city_code", nullable = false, unique = true)
    private String cityCode;

    @Column(name = "city_name", nullable = false)
    private String cityName;

    @Column(nullable = false)
    private boolean enabled;

    protected CityEntity() {
    }

    public UUID id() { return id; }
}
