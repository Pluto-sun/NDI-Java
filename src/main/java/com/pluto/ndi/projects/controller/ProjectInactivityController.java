package com.pluto.ndi.projects.controller;

import java.util.List;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectInactivityService;
import com.pluto.ndi.projects.service.ProjectInactivitySummary;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知中心的督办待处理、历史与转发入口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectInactivityController {
    private final ProjectInactivityService service;
    private final ApiResponseFactory responses;

    public ProjectInactivityController(ProjectInactivityService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/inactivity-notices")
    public ApiResponse<PageResponse<ProjectInactivitySummary>> findMine(Authentication authentication,
            @RequestParam(defaultValue = "false") boolean history,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(PageResponse.from(service.findMine(actorId(authentication), history,
                PageRequest.of(page, size))));
    }

    @GetMapping("/inactivity-cases/{caseId}/recipients")
    public ApiResponse<List<ProjectUser>> recipients(Authentication authentication, @PathVariable UUID caseId) {
        return responses.success(service.recipients(actorId(authentication), caseId));
    }

    public record ForwardRequest(@NotNull UUID recipientId, @NotBlank @Size(max = 2000) String note) { }

    @PostMapping("/inactivity-cases/{caseId}/forward")
    public ApiResponse<Void> forward(Authentication authentication, @PathVariable UUID caseId,
            @Valid @RequestBody ForwardRequest request) {
        service.forward(actorId(authentication), caseId, request.recipientId(), request.note());
        return responses.success(null);
    }

    public record TriggerRequest(@NotNull UUID projectId, @Min(30) @Max(36) int days) { }

    @PostMapping("/admin/inactivity-reminders/trigger")
    public ApiResponse<ProjectInactivityService.ScanResult> trigger(Authentication authentication,
            @Valid @RequestBody TriggerRequest request) {
        return responses.success(service.trigger(actorId(authentication), request.projectId(), request.days()));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
