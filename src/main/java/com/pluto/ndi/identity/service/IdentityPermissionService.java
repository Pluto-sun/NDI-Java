package com.pluto.ndi.identity.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.pluto.ndi.identity.repository.UserAccountRepository;
import com.pluto.ndi.identity.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

@Service
class IdentityPermissionService implements IdentityDirectory {

    private static final Set<String> PROVINCE_LEADER_ROLES = Set.of(
            "PROVINCE_REPORTER",
            "PROVINCE_DIGITAL_LEADER",
            "PROVINCE_INTELLIGENT_LEADER"
    );
    private static final Set<String> PROVINCE_PROJECT_CREATOR_ROLES = PROVINCE_LEADER_ROLES;
    private static final String CITY_PROJECT_CREATOR_ROLE = "CITY_REPORTER";
    private static final String INTERFACE_PROJECT_READER_ROLE = "INTERFACE_REVIEWER";

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;

    IdentityPermissionService(UserAccountRepository userAccountRepository, UserRoleRepository userRoleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public Optional<ProjectActor> findProjectActor(UUID userId) {
        return userAccountRepository.findById(userId)
                .map(account -> new ProjectActor(
                        account.id(),
                        account.provinceId(),
                        account.cityId(),
                        account.enabled()
                ));
    }

    @Override
    public boolean canCreateProject(ProjectActor actor, UUID provinceId, UUID cityId) {
        if (!actor.enabled()) {
            return false;
        }
        var roleCodes = roleCodes(actor.id());
        boolean provinceCreator = provinceId.equals(actor.provinceId())
                && roleCodes.stream().anyMatch(PROVINCE_PROJECT_CREATOR_ROLES::contains);
        boolean cityCreator = provinceId.equals(actor.provinceId())
                && cityId.equals(actor.cityId())
                && roleCodes.contains(CITY_PROJECT_CREATOR_ROLE);
        return provinceCreator || cityCreator;
    }

    @Override
    public Optional<ProjectDataScope> findReadableProjectScope(ProjectActor actor) {
        if (!actor.enabled()) {
            return Optional.empty();
        }
        var roleCodes = roleCodes(actor.id());
        boolean provinceReader = roleCodes.stream().anyMatch(PROVINCE_PROJECT_CREATOR_ROLES::contains)
                || roleCodes.contains(INTERFACE_PROJECT_READER_ROLE);
        if (provinceReader && actor.provinceId() != null) {
            return Optional.of(new ProjectDataScope(ProjectDataScope.Level.PROVINCE, actor.provinceId()));
        }
        if (roleCodes.contains(CITY_PROJECT_CREATOR_ROLE) && actor.cityId() != null) {
            return Optional.of(new ProjectDataScope(ProjectDataScope.Level.CITY, actor.cityId()));
        }
        return Optional.empty();
    }

    @Override
    public boolean isEligibleProvinceLeader(UUID userId, UUID provinceId) {
        return findProjectActor(userId)
                .filter(ProjectActor::enabled)
                .filter(actor -> provinceId.equals(actor.provinceId()))
                .map(actor -> roleCodes(actor.id()))
                .map(roles -> roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains))
                .orElse(false);
    }

    private Set<String> roleCodes(UUID userId) {
        return Set.copyOf(userRoleRepository.findRoleCodesByUserId(userId));
    }
}
