package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectWorkbenchBreakdown;
import com.pluto.ndi.projects.service.ProjectWorkbenchOverview;
import com.pluto.ndi.projects.service.ProjectWorkbenchService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目管理模块专属工作台接口。 */
@RestController
@RequestMapping("/api/v1/projects/workbench")
public class ProjectWorkbenchController {

    private final ProjectWorkbenchService service;
    private final ApiResponseFactory responseFactory;

    public ProjectWorkbenchController(ProjectWorkbenchService service, ApiResponseFactory responseFactory) {
        this.service = service;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/overview")
    public ApiResponse<ProjectWorkbenchOverview> overview(
            Authentication authentication, @RequestParam(defaultValue = "false") boolean includeTerminated
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return responseFactory.success(service.overview(principal.userId(), includeTerminated));
    }

    @GetMapping("/breakdown")
    public ApiResponse<ProjectWorkbenchBreakdown> breakdown(
            Authentication authentication, @RequestParam(defaultValue = "false") boolean includeTerminated
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return responseFactory.success(service.breakdown(principal.userId(), includeTerminated));
    }
}
