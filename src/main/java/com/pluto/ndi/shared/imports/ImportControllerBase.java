package com.pluto.ndi.shared.imports;

import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.shared.web.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** 统一导入HTTP协议，业务控制器只选择引擎及模板策略。 */
public abstract class ImportControllerBase {
    private final TabularImportEngine<?> imports;
    private final ApiResponseFactory responses;

    protected ImportControllerBase(TabularImportEngine<?> imports, ApiResponseFactory responses) {
        this.imports = imports;
        this.responses = responses;
    }

    protected abstract UUID actor(Authentication auth);

    protected abstract byte[] templateBytes(UUID actor) throws java.io.IOException;

    @GetMapping("/imports/task")
    public ApiResponse<Task> current(Authentication auth) {
        return responses.success(imports.current(actor(auth)));
    }

    @PostMapping("/imports/task")
    public ApiResponse<Task> upload(
            Authentication auth,
            @RequestPart MultipartFile file,
            @RequestParam(required = false) Long expectedVersion,
            @RequestParam(required = false) UUID expectedTaskId)
            throws java.io.IOException {
        return responses.success(
                imports.upload(
                        actor(auth),
                        file.getOriginalFilename(),
                        file.getBytes(),
                        expectedVersion,
                        expectedTaskId));
    }

    public record Save(
            @NotNull UUID expectedTaskId,
            @Min(0) long expectedVersion,
            int step,
            @NotNull Configuration configuration,
            List<Integer> selectedRowNumbers) {}

    @PutMapping("/imports/task")
    public ApiResponse<Task> save(Authentication auth, @Valid @RequestBody Save body) {
        return responses.success(
                imports.save(
                        actor(auth),
                        body.expectedTaskId(),
                        body.expectedVersion(),
                        body.step(),
                        body.configuration(),
                        body.selectedRowNumbers()));
    }

    @DeleteMapping("/imports/task")
    public ApiResponse<Void> discard(
            Authentication auth,
            @RequestParam UUID expectedTaskId,
            @RequestParam long expectedVersion) {
        imports.discard(actor(auth), expectedTaskId, expectedVersion);
        return responses.success(null);
    }

    @GetMapping("/imports/template")
    public ResponseEntity<byte[]> template(Authentication auth) throws java.io.IOException {
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateBytes(actor(auth)));
    }

    @GetMapping("/imports/{id}")
    public ApiResponse<Batch> batch(Authentication auth, @PathVariable UUID id) {
        return responses.success(imports.batch(actor(auth), id));
    }

    public record Revise(long expectedRevision, @NotNull Map<String, String> changes) {}

    @PutMapping("/imports/{id}/rows/{row}")
    public ApiResponse<Batch> revise(
            Authentication auth,
            @PathVariable UUID id,
            @PathVariable int row,
            @Valid @RequestBody Revise body) {
        return responses.success(
                imports.revise(actor(auth), id, row, body.expectedRevision(), body.changes()));
    }

    public record Confirm(@NotEmpty List<Integer> selectedRowNumbers) {}

    @PostMapping("/imports/{id}/confirm")
    public ApiResponse<Batch> confirm(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody Confirm body) {
        return responses.success(imports.confirm(actor(auth), id, body.selectedRowNumbers()));
    }
}
