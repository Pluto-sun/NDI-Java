package com.pluto.ndi.capabilities;

import static com.pluto.ndi.capabilities.GewuDeploymentService.*;

import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.ImportControllerBase;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

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

    @GetMapping
    public ApiResponse<Listing> list(
            Authentication auth,
            @RequestParam(required = false) UUID provinceId,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String deploymentVersion,
            @RequestParam(defaultValue = "") String connected,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(
                deployments.list(
                        actor(auth),
                        provinceId,
                        cityId,
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
        deployments.scope(actor);
        return ExcelWorkbook.template(FIELDS);
    }
}
