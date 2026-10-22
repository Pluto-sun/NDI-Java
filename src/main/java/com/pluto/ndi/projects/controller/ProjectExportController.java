package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectExportDownload;
import com.pluto.ndi.projects.service.ProjectExportService;
import com.pluto.ndi.projects.service.ProjectExportSummary;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目列表导出任务的提交、状态和下载入口。 */
@RestController
@RequestMapping("/api/v1/projects/exports")
public class ProjectExportController {

    private final ProjectExportService service;
    private final ApiResponseFactory responses;

    public ProjectExportController(ProjectExportService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ProjectExportSummary> submit(Authentication authentication,
                                                    @Valid @RequestBody ProjectExportRequest request) {
        return responses.success(service.submit(actorId(authentication), request.toQuery(), request.fields()));
    }

    @GetMapping("/fields")
    public ApiResponse<java.util.List<ProjectExportService.FieldOption>> fields(Authentication authentication) {
        return responses.success(service.fields(actorId(authentication)));
    }

    @GetMapping("/{exportId}")
    public ApiResponse<ProjectExportSummary> find(Authentication authentication, @PathVariable UUID exportId) {
        return responses.success(service.find(actorId(authentication), exportId));
    }

    @GetMapping("/{exportId}/download")
    public ApiResponse<ProjectExportDownload> download(Authentication authentication, @PathVariable UUID exportId) {
        return responses.success(service.download(actorId(authentication), exportId));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
