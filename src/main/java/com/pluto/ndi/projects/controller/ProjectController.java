package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.projects.service.ProjectSummary;
import com.pluto.ndi.projects.service.ProjectDetailSummary;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.PageResponse;
import com.pluto.ndi.shared.security.ActorPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目主档的创建与查询接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ApiResponseFactory responseFactory;

    public ProjectController(ProjectService projectService, ApiResponseFactory responseFactory) {
        this.projectService = projectService;
        this.responseFactory = responseFactory;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectSummary> createProject(
            Authentication authentication,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return responseFactory.success(projectService.createProject(actorId(authentication), request.toCommand()));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectSummary>> findProjects(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不得小于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量不得小于1")
            @Max(value = 100, message = "每页数量不得超过100") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return responseFactory.success(PageResponse.from(projectService.findProjects(actorId(authentication), pageable)));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDetailSummary> findDetail(
            Authentication authentication, @PathVariable UUID projectId
    ) {
        return responseFactory.success(projectService.findDetail(actorId(authentication), projectId));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
