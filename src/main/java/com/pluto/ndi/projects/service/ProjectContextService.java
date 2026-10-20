package com.pluto.ndi.projects.service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectCategory;
import com.pluto.ndi.projects.domain.ProjectApplicationScenario;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目页面的账号上下文和基础字典，不持有前端会话状态。 */
@Service
public class ProjectContextService {

    private final IdentityDirectory identities;
    private final OrganizationDirectory organizations;
    private final ProjectIndustryReferenceRepository industries;

    public ProjectContextService(IdentityDirectory identities, OrganizationDirectory organizations,
            ProjectIndustryReferenceRepository industries) {
        this.identities = identities;
        this.organizations = organizations;
        this.industries = industries;
    }

    @Transactional(readOnly = true)
    public ProjectUser currentUser(UUID actorId) {
        return identities.findProjectUser(actorId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }

    @Transactional(readOnly = true)
    public ProjectReferenceData referenceData(UUID actorId) {
        currentUser(actorId);
        var industryOptions = industries.findByEnabledTrueOrderByIndustryCodeAsc().stream()
                .map(item -> new ProjectReferenceData.CodeLabel(item.industryCode(), item.industryName()))
                .toList();
        return new ProjectReferenceData(organizations.findEnabledProvinces(), organizations.findEnabledCities(),
                industryOptions,
                Arrays.stream(ProjectCategory.values())
                        .map(item -> new ProjectReferenceData.CodeLabel(item.name(), item.label())).toList(),
                Arrays.stream(ProjectStage.values())
                        .map(item -> new ProjectReferenceData.CodeLabel(item.name(), item.label())).toList(),
                Arrays.stream(ProjectApplicationScenario.values())
                        .map(item -> new ProjectReferenceData.CodeLabel(item.name(), item.label())).toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectUser> provinceLeaders(UUID actorId, UUID provinceId) {
        requireProvinceScope(actorId, provinceId);
        return identities.findEligibleProvinceLeaders(provinceId);
    }

    @Transactional(readOnly = true)
    public List<ProjectUser> stageApprovers(UUID actorId, UUID provinceId) {
        requireProvinceScope(actorId, provinceId);
        return identities.findEligibleStageApprovers(provinceId).stream()
                .filter(user -> !user.id().equals(actorId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectUser> majorChangeApprovers(UUID actorId, UUID provinceId, ProjectInitiatorLevel level) {
        requireProvinceScope(actorId, provinceId);
        return identities.findEligibleMajorChangeApprovers(provinceId, level).stream()
                .filter(user -> !user.id().equals(actorId)).toList();
    }

    private void requireProvinceScope(UUID actorId, UUID provinceId) {
        var actor = currentUser(actorId);
        if (!provinceId.equals(actor.provinceId()) && !actor.roleCodes().contains("SYSTEM_ADMIN")) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectUser> localActors() {
        return identities.findEnabledProjectUsers();
    }
}
