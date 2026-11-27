package com.pluto.ndi.production.controller;

import com.pluto.ndi.shared.filter.ListFilters;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.production.mapper.ProductionPlanMapper;
import com.pluto.ndi.production.service.*;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/production/plans")
public class ProductionPlanController extends com.pluto.ndi.shared.imports.ImportControllerBase {
    private final ProductionPlanService plans;
    private final ProductionImportService imports;
    private final ApiResponseFactory responses;

    public ProductionPlanController(
            ProductionPlanService plans,
            ProductionImportService imports,
            ApiResponseFactory responses) {
        super(imports, responses);
        this.plans = plans;
        this.imports = imports;
        this.responses = responses;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummary> dashboard(Authentication auth,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return responses.success(plans.dashboard(actor(auth), from, to));
    }

    @GetMapping
    public ApiResponse<ProductionPlanMapper.Result> list(
            Authentication auth,
            @RequestParam(required = false) java.util.List<String> month,
            @RequestParam(required = false) java.util.List<String> provinceId,
            @RequestParam(required = false) java.util.List<String> cityId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) java.util.List<String> category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(
                plans.list(
                        actor(auth),
                        new ProductionPlanMapper.Filter(
                                search, page, size, month, ListFilters.ids(provinceId), ListFilters.ids(cityId), category)));
    }

    @GetMapping("/reference-data")
    public ApiResponse<ProductionPlanService.Reference> reference(Authentication auth) {
        return responses.success(plans.reference(actor(auth)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductionPlan> detail(Authentication auth, @PathVariable UUID id) {
        return responses.success(plans.detail(actor(auth), id));
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

    protected byte[] templateBytes(UUID actor) throws java.io.IOException {
        plans.reference(actor);
        return ExcelWorkbook.template(ProductionImport.templateFields());
    }

    protected UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(ProductionError.forbidden());
        return actor.userId();
    }
}
