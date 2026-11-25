package com.pluto.ndi.revenue.controller;

import com.pluto.ndi.revenue.service.RevenueOrganizationService;
import com.pluto.ndi.revenue.service.RevenueAccessService;
import com.pluto.ndi.revenue.mapper.RevenueOrganizationMapper.Assignment;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revenue/organizations")
public class RevenueOrganizationController {
    private final RevenueOrganizationService service;
    private final ApiResponseFactory responses;

    public RevenueOrganizationController(RevenueOrganizationService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<List<Assignment>> list(Authentication authentication) {
        return responses.success(service.list(actor(authentication)));
    }

    public record Change(UUID provinceId) {}

    @PutMapping("/{key}")
    public ApiResponse<Assignment> update(Authentication authentication, @PathVariable String key,
            @RequestBody Change change) {
        return responses.success(service.update(actor(authentication), key, change.provinceId()));
    }

    private UUID actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal))
            throw RevenueAccessService.forbidden();
        return principal.userId();
    }
}
