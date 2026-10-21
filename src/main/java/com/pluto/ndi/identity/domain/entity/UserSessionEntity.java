package com.pluto.ndi.identity.domain.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** 服务端只保存令牌摘要，凭据版本用于立即撤销旧密码下的会话。 */
@Entity
@Table(name = "identity_user_session")
public class UserSessionEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)") private String tokenHash;
    @Column(name = "credential_version", nullable = false) private long credentialVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;

    protected UserSessionEntity() { }

    public static UserSessionEntity issue(UserAccountEntity user, String hash, Instant now, Instant expires) {
        var session = new UserSessionEntity(); session.id = UUID.randomUUID(); session.userId = user.id();
        session.tokenHash = hash; session.credentialVersion = user.credentialVersion();
        session.createdAt = now; session.expiresAt = expires;
        return session;
    }
}
