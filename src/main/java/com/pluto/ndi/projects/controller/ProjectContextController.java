package com.pluto.ndi.projects.controller;

import java.util.List;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectContextService;
import com.pluto.ndi.projects.service.ProjectReferenceData;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目模块专属的账号与字典入口。 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectContextController {

    private final ProjectContextService service;
    private final ApiResponseFactory responses;

    public ProjectContextController(ProjectContextService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/me")
    public ApiResponse<ProjectUser> me(Authentication authentication) {
        return responses.success(service.currentUser(actorId(authentication)));
    }

    @GetMapping("/reference-data")
    public ApiResponse<ProjectReferenceData> referenceData(Authentication authentication) {
        return responses.success(service.referenceData(actorId(authentication)));
    }

    @GetMapping("/province-leaders")
    public ApiResponse<List<ProjectUser>> provinceLeaders(Authentication authentication,
            @RequestParam UUID provinceId) {
        return responses.success(service.provinceLeaders(actorId(authentication), provinceId));
    }

    @GetMapping("/stage-approvers")
    public ApiResponse<List<ProjectUser>> stageApprovers(Authentication authentication,
            @RequestParam UUID provinceId) {
        return responses.success(service.stageApprovers(actorId(authentication), provinceId));
    }

    @GetMapping("/major-change-approvers")
    public ApiResponse<List<ProjectUser>> majorChangeApprovers(Authentication authentication,
            @RequestParam UUID provinceId, @RequestParam ProjectInitiatorLevel initiatorLevel) {
        return responses.success(service.majorChangeApprovers(actorId(authentication), provinceId, initiatorLevel));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
