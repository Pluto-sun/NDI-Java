package com.pluto.ndi.projects.controller;

import java.util.UUID;
import java.util.List;

import com.pluto.ndi.documents.service.DocumentSummary;
import com.pluto.ndi.documents.service.DownloadIntent;
import com.pluto.ndi.documents.service.UploadIntent;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ProjectDocumentService;
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

/** 项目资料的直传凭证与完成校验接口。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
public class ProjectDocumentController {

    private final ProjectDocumentService service;
    private final ApiResponseFactory responseFactory;

    public ProjectDocumentController(ProjectDocumentService service, ApiResponseFactory responseFactory) {
        this.service = service;
        this.responseFactory = responseFactory;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadIntent> beginUpload(
            Authentication authentication, @PathVariable UUID projectId,
            @Valid @RequestBody BeginProjectUploadRequest request
    ) {
        return responseFactory.success(service.beginUpload(
                actorId(authentication), projectId, request.purpose(), request.toCommand()));
    }

    @PostMapping("/{documentId}/complete")
    public ApiResponse<DocumentSummary> complete(
            Authentication authentication, @PathVariable UUID projectId, @PathVariable UUID documentId
    ) {
        return responseFactory.success(service.completeUpload(actorId(authentication), projectId, documentId));
    }

    @GetMapping("/{documentId}/download-url")
    public ApiResponse<DownloadIntent> download(
            Authentication authentication, @PathVariable UUID projectId, @PathVariable UUID documentId
    ) {
        return responseFactory.success(service.download(actorId(authentication), projectId, documentId));
    }

    @GetMapping
    public ApiResponse<List<DocumentSummary>> list(Authentication authentication, @PathVariable UUID projectId) {
        return responseFactory.success(service.listAvailable(actorId(authentication), projectId));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
