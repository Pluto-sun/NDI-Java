package com.pluto.ndi.identity.service;

import java.util.Map;
import com.pluto.ndi.identity.config.BootstrapAdminProperties;
import com.pluto.ndi.identity.domain.entity.*;
import com.pluto.ndi.identity.repository.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 显式配置的首次管理员初始化，不覆盖已有可登录管理员。 */
@Service
public class IdentityBootstrapService {
    private final BootstrapAdminProperties properties;
    private final UserAccountRepository accounts;
    private final UserRoleRepository roles;
    private final PasswordEncoder passwords;
    private final AuditWriter audit;
    public IdentityBootstrapService(BootstrapAdminProperties properties, UserAccountRepository accounts,
            UserRoleRepository roles, PasswordEncoder passwords, AuditWriter audit) {
        this.properties = properties; this.accounts = accounts; this.roles = roles;
        this.passwords = passwords; this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        String login = properties.adminLogin().strip();
        String password = properties.adminPassword();
        if (login.isEmpty() && password.isEmpty()) return;
        if (login.isEmpty() || login.length() > 80 || !IdentityProfileService.validPassword(password)) {
            throw new IllegalStateException("首次管理员配置不完整，账号最长 80 字符，密码须为 6 至 128 字符");
        }
        accounts.lockAdministration();
        if (accounts.countUsableAdministrators() > 0) return;
        var user = accounts.lockByLoginName(login).orElse(null);
        if (user != null) {
            if (!user.enabled() || !roles.findRoleCodesByUserId(user.id()).contains("SYSTEM_ADMIN")) {
                throw new IllegalStateException("首次管理员账号与已有非管理员账号或停用账号冲突");
            }
            user.changePassword(passwords.encode(password));
        } else {
            user = accounts.saveAndFlush(UserAccountEntity.create(login, "系统管理员", passwords.encode(password)));
            roles.save(UserRoleEntity.assign(user.id(), "SYSTEM_ADMIN"));
        }
        audit.record(user.id(), "IDENTITY_ACCOUNT", user.id(), "IDENTITY_ADMIN_BOOTSTRAPPED", Map.of());
    }
}
