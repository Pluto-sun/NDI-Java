package com.pluto.ndi.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.pluto.ndi.identity.domain.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.*;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {
    @Query("select session.userId from UserSessionEntity session, UserAccountEntity account "
            + "where session.userId = account.id and session.tokenHash = :hash and session.revokedAt is null "
            + "and session.expiresAt > :now and account.enabled = true "
            + "and session.credentialVersion = account.credentialVersion")
    Optional<UUID> findActiveActor(String hash, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update UserSessionEntity session set session.revokedAt = :now "
            + "where session.userId = :userId and session.tokenHash = :hash and session.revokedAt is null")
    int revoke(UUID userId, String hash, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update UserSessionEntity session set session.revokedAt = :now "
            + "where session.userId = :userId and session.revokedAt is null")
    int revokeAll(UUID userId, Instant now);
}
