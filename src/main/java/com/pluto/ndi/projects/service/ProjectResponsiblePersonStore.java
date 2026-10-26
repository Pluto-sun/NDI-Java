package com.pluto.ndi.projects.service;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectResponsiblePerson;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.mapper.ProjectResponsiblePersonMapper;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProjectResponsiblePersonStore {
    private final ProjectResponsiblePersonMapper mapper;
    private final IdentityDirectory identity;

    public ProjectResponsiblePersonStore(
            ProjectResponsiblePersonMapper mapper, IdentityDirectory identity) {
        this.mapper = mapper;
        this.identity = identity;
    }

    public Map<UUID, List<ProjectResponsiblePerson>> find(Collection<UUID> ids) {
        return mapper.find(ids);
    }

    public List<ProjectResponsiblePerson> find(UUID id) {
        return find(List.of(id)).getOrDefault(id, List.of());
    }

    public UUID provinceAccount(UUID id) {
        return find(id).stream()
                .filter(p -> "PROVINCE_LEADER".equals(p.responsibility()))
                .map(ProjectResponsiblePerson::userId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public static List<String> names(String input) {
        return normalizeNames(
                input == null ? List.of() : Arrays.asList(input.split("[;；\\r\\n]+")));
    }

    public static List<String> normalizeNames(List<String> input) {
        if (input == null || input.isEmpty() || input.size() > 100) throw invalid();
        // 同名并不代表同一人，保留次序和独立记录，后续由用户绑定账号区分。
        var output = new ArrayList<String>();
        for (var value : input) {
            if (value == null
                    || value.isBlank()
                    || value.strip().length() > 100
                    || value.codePoints().anyMatch(Character::isISOControl)) throw invalid();
            output.add(value.strip());
        }
        return List.copyOf(output);
    }

    private static BusinessException invalid() {
        return new BusinessException(ProjectErrorCode.INVALID_RESPONSIBLE_PERSONS);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(ProjectEntity project, List<String> names) {
        initialize(project, names, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(ProjectEntity project, List<String> names, String legacyProvinceName) {
        if (names != null) {
            mapper.replace(
                    project.id(),
                    normalizeNames(names).stream()
                            .map(
                                    name ->
                                            new ProjectResponsiblePerson(
                                                    UUID.randomUUID(),
                                                    name,
                                                    null,
                                                    null,
                                                    null,
                                                    false))
                            .toList());
        } else {
            var people = legacyPeople(project, legacyProvinceName);
            if (people.isEmpty() || people.size() > 100) throw invalid();
            mapper.replace(project.id(), people);
        }
    }

    private List<ProjectResponsiblePerson> legacyPeople(
            ProjectEntity project, String legacyProvinceName) {
        var people = new ArrayList<ProjectResponsiblePerson>();
        if (project.provinceLeaderUserId() != null)
            identity.findProjectUser(project.provinceLeaderUserId())
                    .ifPresent(
                            user ->
                                    people.add(
                                            new ProjectResponsiblePerson(
                                                    UUID.randomUUID(),
                                                    user.displayName(),
                                                    "PROVINCE_LEADER",
                                                    user.id(),
                                                    user.displayName(),
                                                    true)));
        if (project.provinceLeaderUserId() == null
                && legacyProvinceName != null
                && !legacyProvinceName.isBlank())
            people.add(
                    new ProjectResponsiblePerson(
                            UUID.randomUUID(),
                            legacyProvinceName.strip(),
                            "PROVINCE_LEADER",
                            null,
                            null,
                            false));
        if (project.cityAccountManagerName() != null && !project.cityAccountManagerName().isBlank())
            people.add(
                    new ProjectResponsiblePerson(
                            UUID.randomUUID(),
                            project.cityAccountManagerName(),
                            "CITY_MANAGER",
                            null,
                            null,
                            false));
        for (var name : project.productSupportExpertNames())
            people.add(
                    new ProjectResponsiblePerson(
                            UUID.randomUUID(), name, "SUPPORT_EXPERT", null, null, false));
        return people;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronizeLegacy(ProjectEntity project) {
        var existing = find(project.id());
        var retained = existing.stream().filter(person -> person.responsibility() == null).toList();
        var legacy =
                legacyPeople(project, null).stream()
                        .map(
                                person ->
                                        existing.stream()
                                                .filter(
                                                        old ->
                                                                Objects.equals(
                                                                                old.name(),
                                                                                person.name())
                                                                        && Objects.equals(
                                                                                old
                                                                                        .responsibility(),
                                                                                person
                                                                                        .responsibility())
                                                                        && Objects.equals(
                                                                                old.userId(),
                                                                                person.userId()))
                                                .findFirst()
                                                .orElse(person))
                        .toList();
        var merged = new ArrayList<ProjectResponsiblePerson>(retained);
        merged.addAll(legacy);
        if (merged.isEmpty() || merged.size() > 100) throw invalid();
        var users = new HashSet<UUID>();
        for (var person : merged)
            if (person.userId() != null && !users.add(person.userId())) throw invalid();
        mapper.replace(project.id(), merged);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(ProjectEntity project, List<ProjectResponsiblePerson> input) {
        if (input == null || input.isEmpty() || input.size() > 100) throw invalid();
        var existing =
                find(project.id()).stream()
                        .map(ProjectResponsiblePerson::id)
                        .collect(java.util.stream.Collectors.toSet());
        var ids = new HashSet<UUID>();
        var users = new HashSet<UUID>();
        int province = 0;
        var people = new ArrayList<ProjectResponsiblePerson>();
        for (var person : input) {
            if (person == null
                    || person.name() == null
                    || person.name().isBlank()
                    || person.name().strip().length() > 100
                    || person.name().codePoints().anyMatch(Character::isISOControl))
                throw invalid();
            String role = person.responsibility();
            if (role != null
                    && !Set.of("PROVINCE_LEADER", "CITY_MANAGER", "SUPPORT_EXPERT").contains(role))
                throw invalid();
            if ("PROVINCE_LEADER".equals(role) && ++province > 1) throw invalid();
            UUID id = person.id() == null ? UUID.randomUUID() : person.id();
            if (person.id() != null && !existing.contains(id) || !ids.add(id)) throw invalid();
            if (person.userId() != null) {
                if (!users.add(person.userId())
                        || identity.findProjectActor(person.userId())
                                .filter(actor -> actor.enabled())
                                .isEmpty()) throw invalid();
                if ("PROVINCE_LEADER".equals(role)
                        && (!identity.isEligibleProvinceLeader(
                                        person.userId(), project.provinceId())
                                || identity.isSystemAdministrator(person.userId())))
                    throw new BusinessException(ProjectErrorCode.INVALID_PROVINCE_LEADER);
            }
            people.add(
                    new ProjectResponsiblePerson(
                            id, person.name().strip(), role, person.userId(), null, false));
        }
        mapper.replace(project.id(), people);
    }
}
