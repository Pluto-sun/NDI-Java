package com.pluto.ndi.production.controller;

import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.production.domain.ProductionImport.*;
import com.pluto.ndi.production.mapper.ProductionPlanMapper;
import com.pluto.ndi.production.service.*;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/production/plans")
public class ProductionPlanController {
    private final ProductionPlanService plans;
    private final ProductionImportService imports;
    private final ApiResponseFactory responses;

    public ProductionPlanController(
            ProductionPlanService plans,
            ProductionImportService imports,
            ApiResponseFactory responses) {
        this.plans = plans;
        this.imports = imports;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<ProductionPlanMapper.Result> list(
            Authentication auth,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) UUID provinceId,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(
                plans.list(
                        actor(auth),
                        new ProductionPlanMapper.Filter(
                                month, provinceId, cityId, search, category, page, size)));
    }

    @GetMapping("/reference-data")
    public ApiResponse<ProductionPlanService.Reference> reference(Authentication auth) {
        return responses.success(plans.reference(actor(auth)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductionPlan> create(
            Authentication auth, @RequestBody ProductionPlan.Command body) {
        return responses.success(plans.create(actor(auth), body));
    }

    public record Update(@Min(0) long version, @NotNull ProductionPlan.Command plan) {}

    @PutMapping("/{id}")
    public ApiResponse<ProductionPlan> update(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody Update body) {
        return responses.success(plans.update(actor(auth), id, body.version(), body.plan()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        plans.delete(actor(auth), id, version);
        return responses.success(null);
    }

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
        plans.reference(actor(auth));
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExcelWorkbook.template(ProductionImport.fields()));
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

    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(ProductionError.forbidden());
        return actor.userId();
    }
}
