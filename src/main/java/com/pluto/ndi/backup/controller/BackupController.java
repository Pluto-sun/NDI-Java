package com.pluto.ndi.backup.controller;

import com.pluto.ndi.backup.service.*;
import com.pluto.ndi.backup.domain.BackupErrorCode;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin/backups")
public class BackupController {
    private final BackupTaskService tasks;
    private final ApiResponseFactory responses;
    public BackupController(BackupTaskService tasks, ApiResponseFactory responses) { this.tasks = tasks; this.responses = responses; }
    public record CreateRequest(@NotNull UUID requestId) { }
    public record Listing(boolean enabled, PageResponse<BackupTaskSummary> records) { }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<BackupTaskSummary> create(Authentication auth, @Valid @RequestBody CreateRequest request) {
        return responses.success(tasks.create(actor(auth), request.requestId()));
    }
    @GetMapping
    public ApiResponse<Listing> list(Authentication auth, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return responses.success(new Listing(tasks.enabled(), PageResponse.from(tasks.list(actor(auth), PageRequest.of(page,size)))));
    }
    @GetMapping("/{id}")
    public ApiResponse<BackupTaskSummary> find(Authentication auth, @PathVariable UUID id) { return responses.success(tasks.find(actor(auth),id)); }
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(Authentication auth, @PathVariable UUID id) {
        var download = tasks.openDownload(actor(auth), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(download.bytes()).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.filename()).build().toString())
                .header("X-Checksum-SHA256", download.sha256()).body(new InputStreamResource(download.stream()));
    }
    @PostMapping("/{id}/download-check")
    public ApiResponse<Void> checkDownload(Authentication auth, @PathVariable UUID id) {
        tasks.checkDownload(actor(auth), id);
        return responses.success(null);
    }
    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal principal)) throw new BusinessException(BackupErrorCode.FORBIDDEN);
        return principal.userId();
    }
}
