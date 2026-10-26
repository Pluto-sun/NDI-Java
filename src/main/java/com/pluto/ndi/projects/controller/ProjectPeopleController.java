package com.pluto.ndi.projects.controller;

import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.service.*;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/people")
public class ProjectPeopleController {
    private final ProjectPeopleService service;
    private final ApiResponseFactory responses;

    public ProjectPeopleController(ProjectPeopleService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    public record PersonRequest(
            UUID id, @NotBlank @Size(max = 100) String name, String responsibility, UUID userId) {}

    public record ConfigureRequest(
            @NotNull @Min(0) Long expectedVersion,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid PersonRequest> people) {}

    @PutMapping
    public ApiResponse<ProjectDetailSummary> configure(
            Authentication authentication,
            @PathVariable UUID projectId,
            @Valid @RequestBody ConfigureRequest input) {
        return responses.success(
                service.configure(
                        actor(authentication),
                        projectId,
                        input.expectedVersion(),
                        input.people().stream()
                                .map(
                                        person ->
                                                new ProjectResponsiblePerson(
                                                        person.id(),
                                                        person.name(),
                                                        person.responsibility(),
                                                        person.userId(),
                                                        null,
                                                        false))
                                .toList()));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<ProjectUser>> accounts(
            Authentication authentication,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "false") boolean provinceOnly) {
        return responses.success(
                service.accountOptions(actor(authentication), projectId, keyword, provinceOnly));
    }

    private UUID actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof ActorPrincipal principal))
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        return principal.userId();
    }
}
