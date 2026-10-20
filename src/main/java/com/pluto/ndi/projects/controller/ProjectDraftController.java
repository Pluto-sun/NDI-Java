package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectDraftService;
import com.pluto.ndi.projects.service.ProjectDraftSummary;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目草稿仅限当前创建者管理。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/drafts")
public class ProjectDraftController {
    private final ProjectDraftService service;
    private final ApiResponseFactory responses;

    public ProjectDraftController(ProjectDraftService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectDraftSummary>> findMine(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(PageResponse.from(service.findMine(actorId(authentication), PageRequest.of(page, size))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectDraftSummary> create(Authentication authentication,
            @Valid @RequestBody ProjectDraftRequest request) {
        return responses.success(service.save(actorId(authentication), null, request.title(), request.data()));
    }

    @PutMapping("/{draftId}")
    public ApiResponse<ProjectDraftSummary> update(Authentication authentication, @PathVariable UUID draftId,
            @Valid @RequestBody ProjectDraftRequest request) {
        return responses.success(service.save(actorId(authentication), draftId, request.title(), request.data()));
    }

    @DeleteMapping("/{draftId}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable UUID draftId) {
        service.delete(actorId(authentication), draftId);
        return responses.success(null);
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
