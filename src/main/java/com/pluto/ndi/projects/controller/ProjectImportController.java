package com.pluto.ndi.projects.controller;

import java.io.IOException;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.service.ImportBatchSummary;
import com.pluto.ndi.projects.service.ImportBatchListItem;
import com.pluto.ndi.projects.service.ProjectImportService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 项目 Excel 导入的预检、错误报告与人工确认入口。 */
@Validated
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

    @GetMapping
    public ApiResponse<PageResponse<ImportBatchListItem>> findMine(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return responses.success(PageResponse.from(service.findMine(actorId(authentication), PageRequest.of(page, size))));
    }

    @PostMapping("/{batchId}/confirm")
    public ApiResponse<ImportBatchSummary> confirm(Authentication authentication, @PathVariable UUID batchId,
            @Valid @RequestBody(required = false) ProjectImportConfirmRequest request) {
        return responses.success(service.confirm(actorId(authentication), batchId,
                request == null ? null : request.selectedRowNumbers()));
    }

    @DeleteMapping("/{batchId}")
    public ApiResponse<ImportBatchSummary> cancel(Authentication authentication, @PathVariable UUID batchId) {
        return responses.success(service.cancel(actorId(authentication), batchId));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
