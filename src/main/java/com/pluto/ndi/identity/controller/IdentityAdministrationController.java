package com.pluto.ndi.identity.controller;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.pluto.ndi.identity.domain.IdentityErrorCode;
import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.organization.service.ProvinceOption;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class IdentityAdministrationController {
    private final IdentityAdministrationService service;
    private final ApiResponseFactory responses;
    public IdentityAdministrationController(IdentityAdministrationService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }
    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminAccountView>> users(Authentication auth, @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Boolean enabled, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String role) {
        return responses.success(PageResponse.from(service.list(actorId(auth), search, enabled, page, size, role)));
    }
    @GetMapping("/configuration")
    public ApiResponse<IdentityAdministrationService.Configuration> configuration(Authentication auth) {
        return responses.success(service.configuration(actorId(auth)));
    }
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminAccountView> create(Authentication auth, @Valid @RequestBody CreateRequest body) {
        return responses.success(service.create(actorId(auth), body.account().command(), body.password()));
    }
    @PutMapping("/users/{id}")
    public ApiResponse<AdminAccountView> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        return responses.success(service.update(actorId(auth), id, body.version(), body.account().command()));
    }
    @PatchMapping("/users/{id}/enabled")
    public ApiResponse<AdminAccountView> enabled(Authentication auth, @PathVariable UUID id, @Valid @RequestBody EnabledRequest body) {
        return responses.success(service.setEnabled(actorId(auth), id, body.version(), body.enabled()));
    }
    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<AdminAccountView> resetPassword(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ResetRequest body) {
        return responses.success(service.resetPassword(actorId(auth), id, body.version(), body.newPassword()));
    }
    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CityOption> city(Authentication auth, @Valid @RequestBody CityRequest body) {
        return responses.success(service.createCity(actorId(auth), body.provinceId(), body.name()));
    }
    @PostMapping("/provinces")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProvinceOption> province(Authentication auth,
            @Valid @RequestBody ProvinceRequest body) {
        return responses.success(service.createProvince(actorId(auth), body.name()));
    }
    @PutMapping("/users/{id}/reviewer-provinces")
    public ApiResponse<AdminAccountView> reviewerProvinces(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ReviewerRequest body) {
        return responses.success(service.saveReviewerMappings(actorId(auth), List.of(
                new IdentityAdministrationService.ReviewerMappingCommand(id, body.version(), body.provinceIds()))).getFirst());
    }
    @PutMapping("/reviewer-province-maps")
    public ApiResponse<List<AdminAccountView>> reviewerMappings(Authentication auth, @Valid @RequestBody ReviewerBatchRequest body) {
        return responses.success(service.saveReviewerMappings(actorId(auth), body.reviewers().stream().map(item ->
                new IdentityAdministrationService.ReviewerMappingCommand(item.reviewerId(), item.version(), item.provinceIds())).toList()));
    }
    private UUID actorId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor)) {
            throw new BusinessException(IdentityErrorCode.ADMIN_REQUIRED);
        }
        return actor.userId();
    }
    public record AccountRequest(@NotBlank @Size(max = 100) String loginName, @NotBlank @Size(max = 100) String displayName,
            @Size(max = 254) String email, @NotEmpty @Size(max = 10) List<@NotBlank String> roleCodes, UUID provinceId, UUID cityId) {
        AdminAccountCommand command() { return new AdminAccountCommand(loginName, displayName, email, roleCodes, provinceId, cityId); }
    }
    public record CreateRequest(@NotNull @Valid AccountRequest account, @NotBlank @Size(min = 6, max = 128) String password) {
        @Override public String toString() { return "CreateRequest[凭据已隐藏]"; }
    }
    public record UpdateRequest(@NotNull @Min(0) Long version, @NotNull @Valid AccountRequest account) { }
    public record EnabledRequest(@NotNull @Min(0) Long version, @NotNull Boolean enabled) { }
    public record ResetRequest(@NotNull @Min(0) Long version, @NotBlank @Size(min = 6, max = 128) String newPassword) {
        @Override public String toString() { return "ResetRequest[凭据已隐藏]"; }
    }
    public record CityRequest(@NotNull UUID provinceId, @NotBlank @Size(max = 50) String name) { }
    public record ProvinceRequest(@NotBlank @Size(max = 50) String name) { }
    public record ReviewerRequest(@NotNull @Min(0) Long version, @NotNull @Size(max = 100) List<@NotNull UUID> provinceIds) { }
    public record ReviewerBatchItem(@NotNull UUID reviewerId, @NotNull @Min(0) Long version,
            @NotNull @Size(max = 100) List<@NotNull UUID> provinceIds) { }
    public record ReviewerBatchRequest(@NotEmpty @Size(max = 200) List<@NotNull @Valid ReviewerBatchItem> reviewers) { }
}
