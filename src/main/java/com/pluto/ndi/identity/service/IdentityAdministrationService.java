package com.pluto.ndi.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import com.pluto.ndi.identity.domain.AccountRole;
import com.pluto.ndi.identity.domain.IdentityErrorCode;
import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import com.pluto.ndi.identity.domain.entity.UserRoleEntity;
import com.pluto.ndi.identity.repository.*;
import com.pluto.ndi.organization.service.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IdentityAdministrationService {
    private final UserAccountRepository accounts;
    private final UserRoleRepository roles;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final OrganizationDirectory organizations;
    private final OrganizationAdministrationService organizationAdmin;
    private final AuditWriter audit;
    private final Clock clock;

    public IdentityAdministrationService(UserAccountRepository accounts, UserRoleRepository roles,
            UserSessionRepository sessions, PasswordEncoder passwords, OrganizationDirectory organizations,
            OrganizationAdministrationService organizationAdmin, AuditWriter audit, Clock clock) {
        this.accounts = accounts; this.roles = roles; this.sessions = sessions; this.passwords = passwords;
        this.organizations = organizations; this.organizationAdmin = organizationAdmin; this.audit = audit; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AdminAccountView> list(UUID actorId, String search, Boolean enabled, int page, int size) {
        requireAdministrator(actorId);
        if (page < 0 || size < 1 || size > 100 || search != null && search.length() > 100) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_INVALID);
        }
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = accounts.search(term, pattern, enabled, PageRequest.of(page, size, Sort.by("loginName", "id")));
        var ids = result.getContent().stream().map(UserAccountEntity::id).toList();
        Map<UUID, List<String>> assignments = ids.isEmpty() ? Map.of() : roles.findAssignmentsByUserIds(ids).stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::userId,
                        Collectors.mapping(UserRoleAssignment::roleCode, Collectors.toList())));
        return result.map(user -> view(user, assignments.getOrDefault(user.id(), List.of())));
    }

    @Transactional(readOnly = true)
    public Configuration configuration(UUID actorId) {
        requireAdministrator(actorId);
        return new Configuration(Arrays.stream(AccountRole.values()).map(role -> new RoleOption(role.name(), role.label(), role.scope().name())).toList(),
                organizations.findEnabledProvinces(), organizations.findEnabledCities());
    }

    public AdminAccountView create(UUID actorId, AdminAccountCommand command, String password) {
        lockAdministrator(actorId);
        if (!IdentityProfileService.validPassword(password)) throw new BusinessException(IdentityErrorCode.PASSWORD_INVALID);
        var normalized = validate(command, null);
        var user = UserAccountEntity.create(normalized.loginName(), normalized.displayName(), passwords.encode(password));
        user.updateAdministration(normalized.loginName(), normalized.displayName(), normalized.email(), normalized.provinceId(), normalized.cityId());
        checkDuplicates(user.id(), user.loginName(), user.email());
        try { accounts.saveAndFlush(user); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException(IdentityErrorCode.ACCOUNT_CONFLICT); }
        replaceRoles(user.id(), normalized.roleCodes());
        audit.record(actorId, "IDENTITY_ACCOUNT", user.id(), "IDENTITY_ACCOUNT_CREATED", snapshot(user, normalized.roleCodes()));
        return view(user, normalized.roleCodes());
    }

    public AdminAccountView update(UUID actorId, UUID userId, long version, AdminAccountCommand command) {
        lockAdministrator(actorId);
        var user = requireTarget(userId, version);
        var normalized = validate(command, user);
        if (actorId.equals(userId) && !normalized.roleCodes().contains("SYSTEM_ADMIN")) {
            throw new BusinessException(IdentityErrorCode.SELF_ADMIN_PROTECTED);
        }
        var originalRoles = roles.findRoleCodesByUserId(userId);
        var previous = snapshot(user, originalRoles);
        boolean wasUsableAdmin = usableAdmin(user, originalRoles);
        boolean authorizationChanged = !user.loginName().equals(normalized.loginName())
                || !Objects.equals(user.provinceId(), normalized.provinceId()) || !Objects.equals(user.cityId(), normalized.cityId())
                || !new HashSet<>(originalRoles).equals(new HashSet<>(normalized.roleCodes()));
        checkDuplicates(userId, normalized.loginName(), normalized.email());
        user.updateAdministration(normalized.loginName(), normalized.displayName(), normalized.email(), normalized.provinceId(), normalized.cityId());
        if (authorizationChanged) revoke(user);
        flushAccount();
        replaceRoles(userId, normalized.roleCodes());
        if (wasUsableAdmin && !usableAdmin(user, normalized.roleCodes())) protectLastAdministrator();
        audit.record(actorId, "IDENTITY_ACCOUNT", userId, "IDENTITY_ACCOUNT_UPDATED", Map.of(
                "before", previous, "after", snapshot(user, normalized.roleCodes()), "sessionsRevoked", authorizationChanged));
        return view(user, normalized.roleCodes());
    }

    public AdminAccountView setEnabled(UUID actorId, UUID userId, long version, boolean enabled) {
        lockAdministrator(actorId);
        var user = requireTarget(userId, version);
        if (actorId.equals(userId) && !enabled) throw new BusinessException(IdentityErrorCode.SELF_ADMIN_PROTECTED);
        var assigned = roles.findRoleCodesByUserId(userId);
        boolean wasUsableAdmin = usableAdmin(user, assigned);
        if (user.enabled() != enabled) { user.setEnabled(enabled); revoke(user); }
        flushAccount();
        if (wasUsableAdmin && !enabled) protectLastAdministrator();
        audit.record(actorId, "IDENTITY_ACCOUNT", userId, "IDENTITY_ACCOUNT_ENABLED_CHANGED", Map.of("enabled", enabled));
        return view(user, assigned);
    }

    public AdminAccountView resetPassword(UUID actorId, UUID userId, long version, String newPassword) {
        lockAdministrator(actorId);
        var user = requireTarget(userId, version);
        if (!IdentityProfileService.validPassword(newPassword)) throw new BusinessException(IdentityErrorCode.PASSWORD_INVALID);
        user.changePassword(passwords.encode(newPassword));
        sessions.revokeAll(userId, Instant.now(clock));
        flushAccount();
        audit.record(actorId, "IDENTITY_ACCOUNT", userId, "IDENTITY_PASSWORD_RESET", Map.of());
        return view(user, roles.findRoleCodesByUserId(userId));
    }

    public CityOption createCity(UUID actorId, UUID provinceId, String name) {
        lockAdministrator(actorId);
        return organizationAdmin.createCity(actorId, provinceId, name);
    }

    private void requireAdministrator(UUID id) {
        var user = accounts.findById(id).filter(UserAccountEntity::enabled)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ADMIN_REQUIRED));
        if (!roles.findRoleCodesByUserId(user.id()).contains("SYSTEM_ADMIN")) {
            throw new BusinessException(IdentityErrorCode.ADMIN_REQUIRED);
        }
    }
    private void lockAdministrator(UUID id) {
        // 管理锁先于账号锁，跨实例管理操作串行；取得锁后重新检查操作者资格。
        accounts.lockAdministration();
        accounts.lockById(id).filter(UserAccountEntity::enabled)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ADMIN_REQUIRED));
        requireAdministrator(id);
    }
    private UserAccountEntity requireTarget(UUID id, long expectedVersion) {
        var user = accounts.lockById(id).orElseThrow(() -> new BusinessException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
        if (user.managementVersion() != expectedVersion) throw new BusinessException(IdentityErrorCode.VERSION_CONFLICT);
        return user;
    }
    private void revoke(UserAccountEntity user) {
        user.revokeCredentials(); sessions.revokeAll(user.id(), Instant.now(clock));
    }
    private void replaceRoles(UUID id, List<String> assigned) {
        roles.deleteByUserId(id);
        roles.saveAllAndFlush(assigned.stream().map(code -> UserRoleEntity.assign(id, code)).toList());
    }
    private boolean usableAdmin(UserAccountEntity user, List<String> assigned) {
        return user.enabled() && user.passwordHash() != null && assigned.contains("SYSTEM_ADMIN");
    }
    private void protectLastAdministrator() {
        if (accounts.countUsableAdministrators() == 0) throw new BusinessException(IdentityErrorCode.LAST_ADMIN_PROTECTED);
    }
    private void checkDuplicates(UUID id, String loginName, String email) {
        if (accounts.existsByLoginNameAndIdNot(loginName, id)
                || email != null && accounts.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_CONFLICT);
        }
    }
    private void flushAccount() {
        try { accounts.flush(); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException(IdentityErrorCode.ACCOUNT_CONFLICT); }
    }
    private AdminAccountCommand validate(AdminAccountCommand command, UserAccountEntity existing) {
        String login = command.loginName() == null ? "" : command.loginName().strip();
        String name = command.displayName() == null ? "" : command.displayName().strip();
        String email = command.email() == null || command.email().isBlank() ? null : command.email().strip().toLowerCase(Locale.ROOT);
        if ((!login.matches("1[0-9]{10}") && (existing == null || !login.equals(existing.loginName())))
                || name.isBlank() || name.length() > 100 || email != null && (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
            throw new BusinessException(IdentityErrorCode.ACCOUNT_INVALID);
        }
        var assigned = command.roleCodes();
        List<AccountRole> selected;
        try { selected = assigned.stream().map(AccountRole::valueOf).toList(); }
        catch (IllegalArgumentException exception) { throw new BusinessException(IdentityErrorCode.ROLE_SCOPE_INVALID); }
        if (selected.isEmpty() || selected.size() != new HashSet<>(selected).size()
                || selected.size() > 1 && selected.stream().anyMatch(role -> role.scope() != AccountRole.Scope.PROVINCE)) {
            throw new BusinessException(IdentityErrorCode.ROLE_SCOPE_INVALID);
        }
        var provinceId = command.provinceId(); var cityId = command.cityId();
        boolean validProvince = provinceId != null && organizations.findEnabledProvinces().stream().anyMatch(province -> province.id().equals(provinceId));
        boolean valid = switch (selected.getFirst().scope()) {
            case PROVINCE -> validProvince && cityId == null;
            case CITY -> validProvince && cityId != null && organizations.findEnabledLocation(provinceId, cityId).isPresent();
            case REVIEWER -> cityId == null && (provinceId == null || validProvince);
            case NATIONAL -> provinceId == null && cityId == null;
        };
        if (!valid) throw new BusinessException(IdentityErrorCode.ROLE_SCOPE_INVALID);
        return new AdminAccountCommand(login, name, email, assigned.stream().sorted().toList(), provinceId, cityId);
    }
    private AdminAccountView view(UserAccountEntity user, List<String> assigned) {
        return new AdminAccountView(user.id(), user.loginName(), user.displayName(), user.email(), user.provinceId(),
                user.cityId(), user.enabled(), user.passwordHash() != null, user.managementVersion(), assigned.stream().sorted().toList());
    }

    private Map<String, Object> snapshot(UserAccountEntity user, List<String> assigned) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("loginName", user.loginName()); detail.put("displayName", user.displayName());
        detail.put("email", user.email()); detail.put("provinceId", user.provinceId()); detail.put("cityId", user.cityId());
        detail.put("enabled", user.enabled()); detail.put("roles", assigned.stream().sorted().toList());
        return detail;
    }

    public record RoleOption(String code, String label, String scope) { }
    public record Configuration(List<RoleOption> roles, List<ProvinceOption> provinces, List<CityOption> cities) { }
}
