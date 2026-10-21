package com.pluto.ndi.identity.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pluto.ndi.identity.domain.entity.UserAccountEntity;
import com.pluto.ndi.identity.repository.UserAccountRepository;
import com.pluto.ndi.identity.repository.UserRoleAssignment;
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
    private static final Set<String> NATIONAL_PROJECT_READ_ROLES = Set.of(
            "GROUP_REVIEWER", "DS_TECH", "DS_INTELLIGENCE", "GD_DPASS", SYSTEM_ADMIN_ROLE);

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final com.pluto.ndi.identity.mapper.ReviewerProvinceMapper reviewers;

    IdentityPermissionService(UserAccountRepository userAccountRepository, UserRoleRepository userRoleRepository,
            com.pluto.ndi.identity.mapper.ReviewerProvinceMapper reviewers) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.reviewers = reviewers;
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
    public Optional<ProjectUser> findProjectUser(UUID userId) {
        return userAccountRepository.findById(userId)
                .filter(account -> account.enabled())
                .map(this::toProjectUser);
    }

    @Override
    public List<ProjectUser> findEnabledProjectUsers() {
        return toProjectUsers(userAccountRepository.findByEnabledTrueOrderByDisplayNameAsc()).stream()
                .filter(user -> !user.roleCodes().isEmpty())
                .toList();
    }

    @Override
    public List<ProjectUser> findEligibleProvinceLeaders(UUID provinceId) {
        var provinceUsers = userAccountRepository.findByProvinceIdAndEnabledTrueOrderByDisplayNameAsc(provinceId);
        return toProjectUsers(provinceUsers).stream()
                .filter(user -> user.roleCodes().stream().anyMatch(PROVINCE_LEADER_ROLES::contains))
                .toList();
    }

    @Override
    public List<ProjectUser> findEligibleStageApprovers(UUID provinceId) {
        return toProjectUsers(userAccountRepository.findAllById(reviewers.reviewers(provinceId)).stream().filter(UserAccountEntity::enabled).toList()).stream()
                .filter(user -> user.roleCodes().contains(INTERFACE_PROJECT_READER_ROLE)
                        && !user.roleCodes().contains(SYSTEM_ADMIN_ROLE))
                .toList();
    }

    @Override
    public List<ProjectUser> findEligibleMajorChangeApprovers(UUID provinceId,
            ProjectInitiatorLevel initiatorLevel) {
        if (initiatorLevel == ProjectInitiatorLevel.PROVINCE) return findEligibleStageApprovers(provinceId);
        return provinceUsers(provinceId).stream()
                .filter(user -> !user.roleCodes().contains(SYSTEM_ADMIN_ROLE))
                .filter(user -> user.roleCodes().stream().anyMatch(PROVINCE_LEADER_ROLES::contains))
                .toList();
    }

    private List<ProjectUser> provinceUsers(UUID provinceId) {
        return toProjectUsers(userAccountRepository.findByProvinceIdAndEnabledTrueOrderByDisplayNameAsc(provinceId));
    }

    private ProjectUser toProjectUser(UserAccountEntity account) {
        return new ProjectUser(account.id(), account.displayName(), account.provinceId(), account.cityId(),
                roleCodes(account.id()).stream().sorted().toList(), reviewers.provinces(account.id()));
    }

    private List<ProjectUser> toProjectUsers(List<UserAccountEntity> accounts) {
        if (accounts.isEmpty()) return List.of();
        var ids = accounts.stream().map(UserAccountEntity::id).toList();
        var mappedProvinces = reviewers.assignments(ids);
        Map<UUID, List<String>> rolesByUser = userRoleRepository.findAssignmentsByUserIds(ids).stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::userId,
                        Collectors.mapping(UserRoleAssignment::roleCode, Collectors.toList())));
        return accounts.stream().map(account -> new ProjectUser(account.id(), account.displayName(),
                account.provinceId(), account.cityId(),
                rolesByUser.getOrDefault(account.id(), List.of()).stream().sorted().toList(),
                mappedProvinces.getOrDefault(account.id(), List.of()))).toList();
    }

    @Override
    public boolean canCreateProject(ProjectActor actor, UUID provinceId, UUID cityId) {
        if (!actor.enabled()) {
            return false;
        }
        var roleCodes = roleCodes(actor.id());
        if (roleCodes.contains(SYSTEM_ADMIN_ROLE)) return false;
        boolean provinceCreator = provinceId.equals(actor.provinceId())
                && roleCodes.stream().anyMatch(PROVINCE_PROJECT_CREATOR_ROLES::contains);
        boolean cityCreator = provinceId.equals(actor.provinceId())
                && cityId.equals(actor.cityId())
                && roleCodes.contains(CITY_PROJECT_CREATOR_ROLE);
        return provinceCreator || cityCreator;
    }

    @Override
    public boolean canSaveProjectDraft(ProjectActor actor) {
        if (!actor.enabled()) return false;
        var roles = roleCodes(actor.id());
        if (roles.contains(SYSTEM_ADMIN_ROLE)) return false;
        return roles.contains(CITY_PROJECT_CREATOR_ROLE)
                || roles.stream().anyMatch(PROVINCE_PROJECT_CREATOR_ROLES::contains);
    }

    @Override
    public Optional<ProjectDataScope> findReadableProjectScope(ProjectActor actor) {
        if (!actor.enabled()) {
            return Optional.empty();
        }
        var roleCodes = roleCodes(actor.id());
        if (roleCodes.stream().anyMatch(NATIONAL_PROJECT_READ_ROLES::contains)) {
            return Optional.of(new ProjectDataScope(ProjectDataScope.Level.NATIONAL, null));
        }
        boolean provinceReader = roleCodes.stream().anyMatch(PROVINCE_PROJECT_CREATOR_ROLES::contains);
        if (roleCodes.contains(INTERFACE_PROJECT_READER_ROLE)) {
            var provinces = new java.util.HashSet<>(reviewers.provinces(actor.id()));
            if (provinceReader && actor.provinceId() != null) provinces.add(actor.provinceId());
            return provinces.isEmpty() ? Optional.empty() : Optional.of(ProjectDataScope.provinces(provinces));
        }
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
    public Optional<UUID> findEligibleProvinceLeaderByName(String displayName, UUID provinceId) {
        var matches = userAccountRepository.findByDisplayNameAndProvinceIdAndEnabledTrue(displayName, provinceId)
                .stream().map(account -> account.id())
                .filter(id -> isEligibleProvinceLeader(id, provinceId)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    @Override
    public boolean canImportProject(ProjectActor actor, UUID provinceId, UUID cityId) {
        if (!actor.enabled()) {
            return false;
        }
        var roles = roleCodes(actor.id());
        if (roles.contains(SYSTEM_ADMIN_ROLE)) {
            return true;
        }
        if (roles.contains(INTERFACE_PROJECT_READER_ROLE) && reviewers.provinces(actor.id()).contains(provinceId)) return true;
        if (!provinceId.equals(actor.provinceId())) {
            return false;
        }
        return roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains)
                || cityId.equals(actor.cityId()) && roles.contains(CITY_PROJECT_CREATOR_ROLE);
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
        if (initiatorLevel == ProjectInitiatorLevel.PROVINCE) return isEligibleStageApprover(userId, provinceId);
        return findApprovalScope(userId).provinceLeaderProvinceIds().contains(provinceId);
    }

    @Override
    public boolean canAdvanceProjectStage(ProjectActor actor, UUID provinceId, UUID cityId, ProjectInitiatorLevel level) {
        return canInitiateMajorChange(actor, provinceId, cityId, level);
    }

    @Override
    public boolean isEligibleStageApprover(UUID userId, UUID provinceId) {
        return findApprovalScope(userId).interfaceProvinceIds().contains(provinceId);
    }

    @Override
    public ProjectApprovalScope findApprovalScope(UUID actorId) {
        var actor = findProjectActor(actorId).filter(ProjectActor::enabled).orElse(null);
        if (actor == null) return ProjectApprovalScope.none();
        var roles = roleCodes(actorId);
        if (roles.contains(SYSTEM_ADMIN_ROLE)) return ProjectApprovalScope.none();
        var interfaceProvinces = roles.contains(INTERFACE_PROJECT_READER_ROLE) ? Set.copyOf(reviewers.provinces(actorId)) : Set.<UUID>of();
        var leaderProvinces = actor.provinceId() != null && roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains)
                ? Set.of(actor.provinceId()) : Set.<UUID>of();
        return new ProjectApprovalScope(interfaceProvinces, leaderProvinces);
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
        if (!actor.enabled()) {
            return false;
        }
        var roles = roleCodes(actor.id());
        if (roles.contains(SYSTEM_ADMIN_ROLE)) {
            return false;
        }
        if (roles.contains(INTERFACE_PROJECT_READER_ROLE) && reviewers.provinces(actor.id()).contains(provinceId)) return true;
        if (!provinceId.equals(actor.provinceId())) return false;
        return roles.stream().anyMatch(PROVINCE_LEADER_ROLES::contains)
                || cityId.equals(actor.cityId()) && roles.contains(CITY_PROJECT_CREATOR_ROLE);
    }

    private Set<String> roleCodes(UUID userId) {
        return Set.copyOf(userRoleRepository.findRoleCodesByUserId(userId));
    }

    @Override
    public boolean isSystemAdministrator(UUID actorId) {
        return findProjectUser(actorId).map(user -> user.roleCodes().contains(SYSTEM_ADMIN_ROLE)).orElse(false);
    }

    @Override
    public List<ProjectUser> findProjectReminderRecipients(UUID provinceId, UUID assignedLeaderId,
            ProjectReminderAudience audience) {
        return switch (audience) {
            case PROVINCE -> {
                var leaders = findEligibleProvinceLeaders(provinceId).stream()
                        .filter(user -> !user.roleCodes().contains(SYSTEM_ADMIN_ROLE)).toList();
                var assigned = leaders.stream().filter(user -> user.id().equals(assignedLeaderId)).toList();
                yield assigned.isEmpty() ? leaders : assigned;
            }
            case INTERFACE -> findEligibleStageApprovers(provinceId);
            case GROUP -> toProjectUsers(userAccountRepository.findEnabledByRoles(
                    Set.of("GROUP_REVIEWER", "DS_TECH", "DS_INTELLIGENCE", "GD_DPASS"))).stream()
                    .filter(user -> !user.roleCodes().contains(SYSTEM_ADMIN_ROLE)).toList();
        };
    }

    @Override
    public Optional<ProjectReminderForwarding> findProjectReminderForwarding(
            UUID actorId, UUID provinceId, UUID cityId) {
        var actor = findProjectUser(actorId).orElse(null);
        if (actor == null || actor.roleCodes().contains(SYSTEM_ADMIN_ROLE)) return Optional.empty();
        if (provinceId.equals(actor.provinceId())
                && actor.roleCodes().stream().anyMatch(PROVINCE_LEADER_ROLES::contains)) {
            var recipients = toProjectUsers(userAccountRepository.findByCityIdAndEnabledTrueOrderByDisplayNameAsc(cityId))
                    .stream().filter(user -> user.roleCodes().contains(CITY_PROJECT_CREATOR_ROLE)
                            && !user.roleCodes().contains(SYSTEM_ADMIN_ROLE)).toList();
            return Optional.of(new ProjectReminderForwarding("PROVINCE_TO_CITY", recipients));
        }
        String route;
        if (isEligibleStageApprover(actorId, provinceId)) {
            route = "INTERFACE_TO_PROVINCE";
        } else if (actor.roleCodes().stream().anyMatch(
                Set.of("GROUP_REVIEWER", "DS_TECH", "DS_INTELLIGENCE", "GD_DPASS")::contains)) {
            route = "GROUP_TO_PROVINCE";
        } else {
            return Optional.empty();
        }
        var recipients = provinceUsers(provinceId).stream()
                .filter(user -> user.roleCodes().contains("PROVINCE_REPORTER")
                        && !user.roleCodes().contains(SYSTEM_ADMIN_ROLE)).toList();
        return Optional.of(new ProjectReminderForwarding(route, recipients));
    }
}
