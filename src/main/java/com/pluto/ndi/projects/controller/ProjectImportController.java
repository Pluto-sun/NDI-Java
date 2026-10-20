package com.pluto.ndi.projects.controller;

import java.io.IOException;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ImportBatchSummary;
import com.pluto.ndi.projects.service.ProjectImportService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 项目 Excel 导入的预检、错误报告与人工确认入口。 */
@RestController
@RequestMapping("/api/v1/projects/imports")
public class ProjectImportController {
    private final ProjectImportService service;
    private final ApiResponseFactory responses;

    public ProjectImportController(ProjectImportService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImportBatchSummary> preview(Authentication authentication,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > 10 * 1024 * 1024 || file.isEmpty()) {
            throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        }
        return responses.success(service.preview(actorId(authentication), file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping("/{batchId}")
    public ApiResponse<ImportBatchSummary> find(Authentication authentication, @PathVariable UUID batchId) {
        return responses.success(service.find(actorId(authentication), batchId));
    }

    @PostMapping("/{batchId}/confirm")
    public ApiResponse<ImportBatchSummary> confirm(Authentication authentication, @PathVariable UUID batchId) {
        return responses.success(service.confirm(actorId(authentication), batchId));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
