package com.pluto.ndi.identity.infrastructure;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_user_role")
class UserRoleEntity {

    @EmbeddedId
    private UserRoleId id;

    protected UserRoleEntity() {
    }

    String roleCode() {
        return id.roleCode;
    }

    @Embeddable
    static class UserRoleId implements Serializable {

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "role_code", nullable = false)
        private String roleCode;

        protected UserRoleId() {
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof UserRoleId that)) {
                return false;
            }
            return Objects.equals(userId, that.userId) && Objects.equals(roleCode, that.roleCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleCode);
        }
    }
}
