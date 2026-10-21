package com.pluto.ndi.identity.service;

import java.time.*;
import java.util.*;
import com.pluto.ndi.identity.domain.IdentityErrorCode;
import com.pluto.ndi.identity.domain.entity.*;
import com.pluto.ndi.identity.repository.*;
import com.pluto.ndi.identity.util.SessionTokens;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.security.*;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentitySessionService implements ActorSessionDirectory {
    private final UserAccountRepository accounts;
    private final UserSessionRepository sessions;
    private final IdentityProfileService profiles;
    private final PasswordEncoder passwords;
    private final AuthenticationProperties properties;
    private final AuditWriter audit;
    private final Clock clock;
    private final String dummyHash;

    public IdentitySessionService(UserAccountRepository accounts, UserSessionRepository sessions,
            IdentityProfileService profiles, PasswordEncoder passwords, AuthenticationProperties properties,
            AuditWriter audit, Clock clock) {
        this.accounts = accounts; this.sessions = sessions; this.profiles = profiles;
        this.passwords = passwords; this.properties = properties; this.audit = audit; this.clock = clock;
        this.dummyHash = passwords.encode(SessionTokens.generate());
    }

    public LoginResult login(String loginName, String password) {
        requireSessionMode();
        var user = accounts.lockByLoginName(loginName == null ? "" : loginName.strip()).orElse(null);
        String hash = user == null || user.passwordHash() == null ? dummyHash : user.passwordHash();
        boolean matches = password != null && passwords.matches(password, hash);
        if (user == null || !user.enabled() || user.passwordHash() == null || !matches) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        var now = Instant.now(clock);
        var expires = now.plus(Duration.ofHours(properties.sessionTtlHours()));
        var token = SessionTokens.generate();
        sessions.save(UserSessionEntity.issue(user, SessionTokens.hash(token), now, expires));
        audit.record(user.id(), "IDENTITY_ACCOUNT", user.id(), "IDENTITY_LOGIN", Map.of());
        return new LoginResult(token, profiles.current(user.id()), expires);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> resolveActor(String token) {
        return SessionTokens.valid(token) ? sessions.findActiveActor(SessionTokens.hash(token), Instant.now(clock)) : Optional.empty();
    }

    public void logout(UUID userId, String token) {
        requireSessionMode();
        if (SessionTokens.valid(token)) sessions.revoke(userId, SessionTokens.hash(token), Instant.now(clock));
        audit.record(userId, "IDENTITY_ACCOUNT", userId, "IDENTITY_LOGOUT", Map.of());
    }

    public void requireSessionMode() {
        if (properties.mode() != AuthenticationProperties.Mode.SESSION) {
            throw new BusinessException(IdentityErrorCode.SESSION_MODE_REQUIRED);
        }
    }

    /** 仅供控制器写入 Cookie，禁止将整个结果序列化为接口响应。 */
    public record LoginResult(String token, IdentityUserView user, Instant expiresAt) {
        @Override public String toString() { return "LoginResult[会话令牌已隐藏]"; }
    }
}
