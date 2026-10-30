package com.pluto.ndi.production.controller;

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

    protected byte[] templateBytes(UUID actor) throws java.io.IOException {
        plans.reference(actor);
        return ExcelWorkbook.template(ProductionImport.fields());
    }

    protected UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(ProductionError.forbidden());
        return actor.userId();
    }
}
