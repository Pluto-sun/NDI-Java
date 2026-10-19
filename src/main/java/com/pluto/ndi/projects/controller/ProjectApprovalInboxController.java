package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ApprovalNotificationSummary;
import com.pluto.ndi.projects.service.ApprovalTaskSummary;
import com.pluto.ndi.projects.service.ProjectApprovalInboxService;
import com.pluto.ndi.projects.service.ProjectApprovalNotificationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 指定审批人的待办与站内通知接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectApprovalInboxController {

    private final ProjectApprovalInboxService inboxService;
    private final ProjectApprovalNotificationService notificationService;
    private final ApiResponseFactory responseFactory;

    public ProjectApprovalInboxController(
            ProjectApprovalInboxService inboxService,
            ProjectApprovalNotificationService notificationService,
            ApiResponseFactory responseFactory
    ) {
        this.inboxService = inboxService;
        this.notificationService = notificationService;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/approvals/inbox")
    public ApiResponse<PageResponse<ApprovalTaskSummary>> inbox(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return responseFactory.success(PageResponse.from(
                inboxService.findPending(actorId(authentication), PageRequest.of(page, size))));
    }

    @GetMapping("/approval-notifications")
    public ApiResponse<PageResponse<ApprovalNotificationSummary>> notifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return responseFactory.success(PageResponse.from(
                notificationService.findMine(actorId(authentication), PageRequest.of(page, size))));
    }

    @PostMapping("/approval-notifications/{notificationId}/read")
    public ApiResponse<ApprovalNotificationSummary> markRead(
            Authentication authentication, @PathVariable UUID notificationId
    ) {
        return responseFactory.success(notificationService.markRead(actorId(authentication), notificationId));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
