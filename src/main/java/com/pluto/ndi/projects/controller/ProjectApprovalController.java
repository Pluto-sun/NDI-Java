package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ApprovalSummary;
import com.pluto.ndi.projects.service.ApprovalWithdrawalSummary;
import com.pluto.ndi.projects.service.ProjectApprovalService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目重大变更与阶段推进申请的查看、撤销和审批接口。 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectApprovalController {

    private final ProjectApprovalService service;
    private final ApiResponseFactory responseFactory;

    public ProjectApprovalController(ProjectApprovalService service, ApiResponseFactory responseFactory) {
        this.service = service;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/{projectId}/major-changes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApprovalSummary> submit(
            Authentication authentication, @PathVariable UUID projectId, @Valid @RequestBody MajorChangeRequest request
    ) {
        return responseFactory.success(service.submitMajorChange(actorId(authentication), projectId, request.toCommand()));
    }

    @GetMapping("/approvals/{requestId}")
    public ApiResponse<ApprovalSummary> find(Authentication authentication, @PathVariable UUID requestId) {
        return responseFactory.success(service.findApproval(actorId(authentication), requestId));
    }

    @PostMapping("/approvals/{requestId}/withdraw")
    public ApiResponse<ApprovalWithdrawalSummary> withdraw(Authentication authentication, @PathVariable UUID requestId) {
        var result = service.withdraw(actorId(authentication), requestId);
        return responseFactory.success(new ApprovalWithdrawalSummary(result.id(), result.status(), result.decidedAt()));
    }

    @PostMapping("/approvals/{requestId}/decision")
    public ApiResponse<ApprovalSummary> decide(
            Authentication authentication, @PathVariable UUID requestId, @Valid @RequestBody ApprovalDecisionRequest decision
    ) {
        ApprovalSummary result;
        try {
            result = service.decide(actorId(authentication), requestId, decision.approved(), decision.opinion());
        } catch (DataIntegrityViolationException exception) {
            var message = exception.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_project_project_contract_no")) {
                throw new BusinessException(ProjectErrorCode.CONTRACT_NO_CONFLICT);
            }
            throw exception;
        }
        if (result.status() == ApprovalStatus.CONFLICT) {
            throw new BusinessException(ProjectErrorCode.APPROVAL_VERSION_CONFLICT);
        }
        return responseFactory.success(result);
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
