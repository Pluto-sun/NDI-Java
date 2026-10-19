package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectMilestoneSummary;
import com.pluto.ndi.projects.service.ProjectProgressService;
import com.pluto.ndi.projects.service.ProjectProgressSummary;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目进展与阶段里程碑的时间线接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectProgressController {

    private final ProjectProgressService service;
    private final ApiResponseFactory responseFactory;

    public ProjectProgressController(ProjectProgressService service, ApiResponseFactory responseFactory) {
        this.service = service;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/progress")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectProgressSummary> append(
            Authentication authentication, @PathVariable UUID projectId,
            @Valid @RequestBody AppendProjectProgressRequest request
    ) {
        return responseFactory.success(service.append(actorId(authentication), projectId, request.toCommand()));
    }

    @GetMapping("/progress")
    public ApiResponse<PageResponse<ProjectProgressSummary>> progress(
            Authentication authentication, @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return responseFactory.success(PageResponse.from(
                service.findProgress(actorId(authentication), projectId, PageRequest.of(page, size))));
    }

    @GetMapping("/milestones")
    public ApiResponse<PageResponse<ProjectMilestoneSummary>> milestones(
            Authentication authentication, @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return responseFactory.success(PageResponse.from(
                service.findMilestones(actorId(authentication), projectId, PageRequest.of(page, size))));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
