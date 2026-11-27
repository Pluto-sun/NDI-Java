package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.filter.ListFilters;

import static com.pluto.ndi.capabilities.GewuDeploymentService.*;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.ImportControllerBase;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/capability/gewu-deployments")
public class GewuDeploymentController extends ImportControllerBase {
    private final GewuDeploymentService deployments;
    private final ApiResponseFactory responses;

    public GewuDeploymentController(
            GewuDeploymentService deployments,
            GewuImportService imports,
            ApiResponseFactory responses) {
        super(imports, responses);
        this.deployments = deployments;
        this.responses = responses;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummary> dashboard(Authentication auth) {
        return responses.success(deployments.dashboard(actor(auth)));
    }

    @GetMapping
    public ApiResponse<Listing> list(
            Authentication auth,
            @RequestParam(required = false) List<String> provinceId,
            @RequestParam(required = false) List<String> cityId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String deploymentVersion,
            @RequestParam(required = false) List<String> connected,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(
                deployments.list(
                        actor(auth),
                        ListFilters.ids(provinceId),
                        ListFilters.ids(cityId),
                        search,
                        deploymentVersion,
                        connected,
                        page,
                        size));
    }

    @GetMapping("/reference-data")
    public ApiResponse<Reference> reference(Authentication auth) {
        return responses.success(deployments.reference(actor(auth)));
    }

    @GetMapping("/{id}")
    public ApiResponse<Deployment> detail(Authentication auth, @PathVariable UUID id) {
        return responses.success(deployments.detail(actor(auth), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Deployment> create(Authentication auth, @RequestBody Input body) {
        return responses.success(deployments.create(actor(auth), body));
    }

    public record Update(long version, Input deployment) {}

    @PutMapping("/{id}")
    public ApiResponse<Deployment> update(
            Authentication auth, @PathVariable UUID id, @RequestBody Update body) {
        return responses.success(
                deployments.update(actor(auth), id, body.version(), body.deployment()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            Authentication auth, @PathVariable UUID id, @RequestParam long version) {
        deployments.delete(actor(auth), id, version);
        return responses.success(null);
    }

    @Override
    protected UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal principal))
            throw failure(HttpStatus.FORBIDDEN, "无格物部署维护权限");
        return principal.userId();
    }

    @Override
    protected byte[] templateBytes(UUID actor) throws IOException {
        var reference = deployments.reference(actor);
        var citiesByProvince = reference.cities().stream()
                .collect(Collectors.groupingBy(CityOption::provinceId,
                        Collectors.mapping(CityOption::name, Collectors.toList())));
        var citiesByProvinceName = new LinkedHashMap<String, List<String>>();
        for (var province : reference.provinces()) {
            citiesByProvinceName.put(province.name(), citiesByProvince.get(province.id()));
        }
        return ExcelWorkbook.template(FIELDS, Set.of(), dropdowns -> {
            dropdowns.dependent("省份", "地市", citiesByProvinceName);
            reference.options().forEach(dropdowns::list);
        });
    }
}
