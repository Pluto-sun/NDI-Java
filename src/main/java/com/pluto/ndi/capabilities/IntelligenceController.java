package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.documents.service.*;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.ImportControllerBase;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.*;

public abstract class IntelligenceController extends ImportControllerBase {
    protected final IntelligenceService service;
    protected final ApiResponseFactory responses;
    private final IntelligenceKind kind;

    protected IntelligenceController(
            IntelligenceService service,
            IntelligenceImport imports,
            ApiResponseFactory responses,
            IntelligenceKind kind) {
        super(imports, responses);
        this.service = service;
        this.responses = responses;
        this.kind = kind;
    }

    protected UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal principal))
            throw GewuDeploymentService.failure(HttpStatus.FORBIDDEN, "无能力指标维护权限");
        return principal.userId();
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummary> dashboard(Authentication auth) {
        return responses.success(service.dashboard(actor(auth), kind));
    }

    @GetMapping
    public ApiResponse<IntelligenceService.Listing> list(
            Authentication auth,
            @RequestParam(required = false) UUID provinceId,
            @RequestParam(defaultValue = "") String reviewer,
            @RequestParam(defaultValue = "") String place,
            @RequestParam(defaultValue = "") String progress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(
                service.list(actor(auth), kind, provinceId, reviewer, place, progress, page, size));
    }

    @GetMapping("/reference-data")
    public ApiResponse<?> reference(Authentication auth) {
        return responses.success(
                Map.of(
                        "provinces",
                        service.provinces(actor(auth)),
                        "fields",
                        kind.fields,
                        "required",
                        kind.required,
                        "options",
                        IntelligenceKind.OPTIONS,
                        "months",
                        IntelligenceKind.MONTHS));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(Authentication auth, @PathVariable UUID id) {
        return responses.success(service.detail(actor(auth), kind, id, false));
    }

    @PostMapping
    public ApiResponse<?> create(
            Authentication auth, @RequestBody IntelligenceService.Input input) {
        return responses.success(service.save(actor(auth), kind, null, null, input));
    }

    public record Update(long version, IntelligenceService.Input entry) {}

    @PutMapping("/{id}")
    public ApiResponse<?> update(
            Authentication auth, @PathVariable UUID id, @RequestBody Update input) {
        return responses.success(
                service.save(actor(auth), kind, id, input.version(), input.entry()));
    }

    protected byte[] templateBytes(UUID actor) throws IOException {
        var textFields = new HashSet<>(IntelligenceKind.MONTHS);
        textFields.add("开发进度");
        return ExcelWorkbook.template(kind.fields, textFields, dropdowns -> {
            dropdowns.list("省分", service.provinces(actor).stream().map(p -> p.name()).toList());
            for (var field : kind.fields) {
                var options = IntelligenceKind.OPTIONS.get(field);
                if (options != null) dropdowns.list(field, options);
            }
        });
    }
}
