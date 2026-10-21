package com.pluto.ndi.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import com.pluto.ndi.identity.domain.IdentityErrorCode;
import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import com.pluto.ndi.identity.repository.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityProfileService {
    private final UserAccountRepository accounts;
    private final UserRoleRepository roles;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final AuditWriter audit;
    private final Clock clock;
    public IdentityProfileService(UserAccountRepository accounts, UserRoleRepository roles,
            UserSessionRepository sessions, PasswordEncoder passwords, AuditWriter audit, Clock clock) {
        this.accounts = accounts; this.roles = roles; this.sessions = sessions;
        this.passwords = passwords; this.audit = audit; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IdentityUserView current(UUID id) {
        return view(accounts.findById(id).filter(UserAccountEntity::enabled)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.SESSION_INVALID)));
    }

    public IdentityUserView update(UUID id, String displayName, String email) {
        var user = requireLocked(id);
        String name = displayName == null ? "" : displayName.strip();
        String normalizedEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.length() > 100 || normalizedEmail != null
                && (normalizedEmail.length() > 254 || !normalizedEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            throw new BusinessException(IdentityErrorCode.PROFILE_INVALID);
        }
        if (normalizedEmail != null && accounts.existsByEmailAndIdNot(normalizedEmail, id)) {
            throw new BusinessException(IdentityErrorCode.EMAIL_CONFLICT);
        }
        user.updateProfile(name, normalizedEmail);
        try { accounts.flush(); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException(IdentityErrorCode.EMAIL_CONFLICT); }
        audit.record(id, "IDENTITY_ACCOUNT", id, "IDENTITY_PROFILE_UPDATED", Map.of());
        return view(user);
    }

    public void changePassword(UUID id, String currentPassword, String newPassword) {
        var user = requireLocked(id);
        if (currentPassword == null || user.passwordHash() == null || !passwords.matches(currentPassword, user.passwordHash())) {
            throw new BusinessException(IdentityErrorCode.CURRENT_PASSWORD_INVALID);
        }
        if (!validPassword(newPassword) || passwords.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(IdentityErrorCode.PASSWORD_INVALID);
        }
        user.changePassword(passwords.encode(newPassword));
        sessions.revokeAll(id, Instant.now(clock));
        audit.record(id, "IDENTITY_ACCOUNT", id, "IDENTITY_PASSWORD_CHANGED", Map.of());
    }

    static boolean validPassword(String password) {
        return password != null && !password.isBlank() && password.length() >= 6 && password.length() <= 128;
    }
    private UserAccountEntity requireLocked(UUID id) {
        return accounts.lockById(id).filter(UserAccountEntity::enabled)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.SESSION_INVALID));
    }
    private IdentityUserView view(UserAccountEntity user) {
        return new IdentityUserView(user.id(), user.loginName(), user.displayName(), user.email(),
                user.provinceId(), user.cityId(), roles.findRoleCodesByUserId(user.id()));
    }
}
