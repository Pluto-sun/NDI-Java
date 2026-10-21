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

    @Column(length = 254) private String email;
    @Column(name = "password_hash") private String passwordHash;
    @Column(name = "credential_version", nullable = false) private long credentialVersion;

    protected UserAccountEntity() {
    }

    public static UserAccountEntity create(String loginName, String displayName, String passwordHash) {
        var account = new UserAccountEntity();
        account.id = UUID.randomUUID(); account.loginName = loginName;
        account.displayName = displayName; account.passwordHash = passwordHash; account.enabled = true;
        return account;
    }

    public String loginName() { return loginName; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public long credentialVersion() { return credentialVersion; }
    public void updateProfile(String displayName, String email) { this.displayName = displayName; this.email = email; }
    public void changePassword(String encoded) { passwordHash = encoded; credentialVersion++; }

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
