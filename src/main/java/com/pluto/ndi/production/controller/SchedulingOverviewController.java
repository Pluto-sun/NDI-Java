package com.pluto.ndi.production.controller;

import com.pluto.ndi.production.domain.ProductionError;
import com.pluto.ndi.production.domain.SchedulingOverview;
import com.pluto.ndi.production.service.SchedulingOverviewService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/plans")
public class SchedulingOverviewController {
    private final SchedulingOverviewService overview;
    private final ApiResponseFactory responses;

    public SchedulingOverviewController(SchedulingOverviewService overview, ApiResponseFactory responses) {
        this.overview = overview;
        this.responses = responses;
    }

    @GetMapping("/scheduling-overview")
    public ApiResponse<SchedulingOverview> load(Authentication auth, @RequestParam String from, @RequestParam String to) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal actor))
            throw new BusinessException(ProductionError.forbidden());
        return responses.success(overview.load(actor.userId(), from, to));
    }
}
