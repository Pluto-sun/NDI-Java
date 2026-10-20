package com.pluto.ndi.identity.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_user_account")
public class UserAccountEntity {

    @Id
    private UUID id;

    @Column(name = "login_name", nullable = false, unique = true)
    private String loginName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "province_id")
    private UUID provinceId;

    @Column(name = "city_id")
    private UUID cityId;

    @Column(nullable = false)
    private boolean enabled;

    protected UserAccountEntity() {
    }

    public UUID id() {
        return id;
    }

    public String displayName() { return displayName; }

    public UUID provinceId() {
        return provinceId;
    }

    public UUID cityId() {
        return cityId;
    }

    public boolean enabled() {
        return enabled;
    }
}
