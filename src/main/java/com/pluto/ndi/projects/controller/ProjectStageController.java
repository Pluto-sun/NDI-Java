package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ApprovalSummary;
import com.pluto.ndi.projects.service.ProjectStageService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目正向阶段推进的申请接口。 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectStageController {

    private final ProjectStageService service;
    private final ApiResponseFactory responseFactory;

    public ProjectStageController(ProjectStageService service, ApiResponseFactory responseFactory) {
        this.service = service;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/{projectId}/stage-advancements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApprovalSummary> submit(
            Authentication authentication, @PathVariable UUID projectId, @Valid @RequestBody StageAdvanceRequest request
    ) {
        return responseFactory.success(service.submit(actorId(authentication), projectId, request.toCommand()));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
