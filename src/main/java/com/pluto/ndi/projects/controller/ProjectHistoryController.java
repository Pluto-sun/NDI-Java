package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ApprovalSummary;
import com.pluto.ndi.projects.service.ProjectChangeSummary;
import com.pluto.ndi.projects.service.ProjectHistoryService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目详情的审批历史与正式变更记录。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ProjectHistoryController {
    private final ProjectHistoryService service;
    private final ApiResponseFactory responses;

    public ProjectHistoryController(ProjectHistoryService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/approvals")
    public ApiResponse<PageResponse<ApprovalSummary>> approvals(Authentication authentication,
            @PathVariable UUID projectId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(PageResponse.from(service.approvals(actorId(authentication), projectId,
                PageRequest.of(page, size))));
    }

    @GetMapping("/changes")
    public ApiResponse<PageResponse<ProjectChangeSummary>> changes(Authentication authentication,
            @PathVariable UUID projectId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(PageResponse.from(service.changes(actorId(authentication), projectId,
                PageRequest.of(page, size))));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
