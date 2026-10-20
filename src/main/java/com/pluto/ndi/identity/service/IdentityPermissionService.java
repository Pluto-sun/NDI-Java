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
    private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";

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
        if (roleCodes.contains(SYSTEM_ADMIN_ROLE)) {
            return Optional.empty();
        }
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

    @Override
    public boolean canInitiateMajorChange(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level) {
        if (!actor.enabled() || !provinceId.equals(actor.provinceId())) {
            return false;
        }
        var roles = roleCodes(actor.id());
        if (roles.contains(SYSTEM_ADMIN_ROLE)) {
            return false;
        }
        return switch (level) {
            case CITY -> cityId.equals(actor.cityId()) && roles.contains(CITY_PROJECT_CREATOR_ROLE);
            case PROVINCE -> roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains);
        };
    }

    @Override
    public boolean isEligibleMajorChangeApprover(UUID userId, UUID provinceId, ProjectInitiatorLevel initiatorLevel) {
        return findProjectActor(userId)
                .filter(ProjectActor::enabled)
                .filter(actor -> provinceId.equals(actor.provinceId()))
                .map(actor -> roleCodes(actor.id()))
                .map(roles -> !roles.contains(SYSTEM_ADMIN_ROLE) && switch (initiatorLevel) {
                    case CITY -> roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains);
                    case PROVINCE -> roles.contains(INTERFACE_PROJECT_READER_ROLE);
                })
                .orElse(false);
    }

    @Override
    public boolean canAdvanceProjectStage(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level) {
        return canInitiateMajorChange(actor, provinceId, cityId, level);
    }

    @Override
    public boolean isEligibleStageApprover(UUID userId, UUID provinceId) {
        return findProjectActor(userId)
                .filter(ProjectActor::enabled)
                .filter(actor -> provinceId.equals(actor.provinceId()))
                .map(actor -> roleCodes(actor.id()))
                .map(roles -> roles.contains(INTERFACE_PROJECT_READER_ROLE) && !roles.contains(SYSTEM_ADMIN_ROLE))
                .orElse(false);
    }

    @Override
    public boolean canUploadProjectDocument(ProjectActor actor, UUID provinceId, UUID cityId) {
        return hasProjectCollaborationScope(actor, provinceId, cityId);
    }

    @Override
    public boolean canAppendProjectProgress(ProjectActor actor, UUID provinceId, UUID cityId) {
        return hasProjectCollaborationScope(actor, provinceId, cityId);
    }

    @Override
    public boolean canUpdateProjectProfile(ProjectActor actor, UUID provinceId, UUID cityId) {
        if (!actor.enabled() || !provinceId.equals(actor.provinceId())) {
            return false;
        }
        var roles = roleCodes(actor.id());
        if (roles.contains(SYSTEM_ADMIN_ROLE)) {
            return false;
        }
        return roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains)
                || cityId.equals(actor.cityId()) && roles.contains(CITY_PROJECT_CREATOR_ROLE);
    }

    private boolean hasProjectCollaborationScope(ProjectActor actor, UUID provinceId, UUID cityId) {
        if (!actor.enabled() || !provinceId.equals(actor.provinceId())) {
            return false;
        }
        var roles = roleCodes(actor.id());
        if (roles.contains(SYSTEM_ADMIN_ROLE)) {
            return false;
        }
        return roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains)
                || roles.contains(INTERFACE_PROJECT_READER_ROLE)
                || cityId.equals(actor.cityId()) && roles.contains(CITY_PROJECT_CREATOR_ROLE);
    }

    private Set<String> roleCodes(UUID userId) {
        return Set.copyOf(userRoleRepository.findRoleCodesByUserId(userId));
    }
}
