package com.pluto.ndi.projects.controller;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectImportTask.Configuration;
import com.pluto.ndi.projects.service.ProjectImportWizardService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/projects/imports/task")
public class ProjectImportTaskController {
    private final ProjectImportWizardService service;
    private final ApiResponseFactory responses;

    public ProjectImportTaskController(
            ProjectImportWizardService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    public record Save(
            @NotNull UUID expectedTaskId,
            @NotNull @Min(0) Long expectedVersion,
            @Min(1) @Max(3) int step,
            @NotNull @Valid Configuration configuration,
            List<@NotNull @Min(1) Integer> selectedRowNumbers) {}

    @GetMapping
    public ApiResponse<ProjectImportWizardService.View> current(Authentication auth) {
        return responses.success(service.current(actor(auth)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProjectImportWizardService.View> upload(
            Authentication auth,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) Long expectedVersion,
            @RequestParam(required = false) UUID expectedTaskId)
            throws IOException {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new BusinessException(ProjectErrorCode.IMPORT_INVALID_FILE);
        return responses.success(
                service.upload(
                        actor(auth),
                        expectedTaskId,
                        expectedVersion,
                        file.getOriginalFilename(),
                        file.getBytes()));
    }

    @PutMapping
    public ApiResponse<ProjectImportWizardService.View> save(
            Authentication auth, @Valid @RequestBody Save input) {
        return responses.success(
                service.save(
                        actor(auth),
                        input.expectedTaskId(),
                        input.expectedVersion(),
                        input.step(),
                        input.configuration(),
                        input.selectedRowNumbers()));
    }

    @DeleteMapping
    public ApiResponse<Void> discard(
            Authentication auth,
            @RequestParam long expectedVersion,
            @RequestParam UUID expectedTaskId) {
        service.discard(actor(auth), expectedTaskId, expectedVersion);
        return responses.success(null);
    }

    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal p))
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        return p.userId();
    }
}
