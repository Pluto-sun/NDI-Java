package com.pluto.ndi.projects.service;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.util.ProjectNameMatcher;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

/** 重名判断口径集中在服务端，在线创建和导入共用名称规范化算法。 */
@Service
public class ProjectDuplicateService {
    private final IdentityDirectory identity;
    private final OrganizationDirectory organizations;
    private final ProjectRepository projects;

    public ProjectDuplicateService(
            IdentityDirectory identity,
            OrganizationDirectory organizations,
            ProjectRepository projects) {
        this.identity = identity;
        this.organizations = organizations;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public ProjectDuplicateCheck check(UUID actorId, UUID cityId, String projectName) {
        var actor =
                identity.findProjectActor(actorId)
                        .filter(ProjectActor::enabled)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN));
        var city =
                organizations.findEnabledCities().stream()
                        .filter(item -> item.id().equals(cityId))
                        .findFirst()
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.INVALID_ORGANIZATION));
        if (!identity.canCreateProject(actor, city.provinceId(), cityId)) {
            throw new BusinessException(ProjectErrorCode.CREATION_FORBIDDEN);
        }
        return candidates(cityId, projectName);
    }

    public ProjectDuplicateCheck candidates(UUID cityId, String projectName) {
        return candidates(cityId, projectName, null);
    }

    public ProjectDuplicateCheck candidates(
            UUID cityId, String projectName, UUID excludedProjectId) {
        if (projectName == null || projectName.isBlank() || projectName.length() > 200) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_NAME);
        }
        var standard = ProjectNameMatcher.standard(projectName);
        if (standard.isEmpty()) throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_NAME);
        var matches =
                projects.findNamesByCityId(cityId).stream()
                        .filter(item -> !item.projectId().equals(excludedProjectId))
                        .map(
                                item ->
                                        new ProjectDuplicateCheck.Candidate(
                                                item.projectId(),
                                                item.projectCode(),
                                                item.projectName(),
                                                ProjectNameMatcher.similarity(
                                                        projectName, item.projectName())))
                        .toList();
        var exact =
                matches.stream()
                        .filter(
                                item ->
                                        ProjectNameMatcher.standard(item.projectName())
                                                .equals(standard))
                        .sorted(Comparator.comparing(ProjectDuplicateCheck.Candidate::projectCode))
                        .limit(5)
                        .toList();
        if (!exact.isEmpty()) return new ProjectDuplicateCheck("STRONG_DUPLICATE", exact);
        var similar =
                matches.stream()
                        .filter(item -> item.similarity() >= 80)
                        .sorted(
                                Comparator.comparingInt(ProjectDuplicateCheck.Candidate::similarity)
                                        .reversed()
                                        .thenComparing(
                                                ProjectDuplicateCheck.Candidate::projectCode))
                        .limit(5)
                        .toList();
        return new ProjectDuplicateCheck(
                similar.isEmpty() ? "NONE" : "DUPLICATE_CANDIDATE", similar);
    }
}
