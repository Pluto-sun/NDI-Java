package com.pluto.ndi.projects.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.service.ProjectDashboard;
import com.pluto.ndi.projects.service.ProjectDashboardService;
import java.util.List;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/workbench/dashboard")
public class ProjectDashboardController {
    private final ProjectDashboardService service;
    private final ApiResponseFactory responses;
    public ProjectDashboardController(ProjectDashboardService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }
    @GetMapping
    public ApiResponse<ProjectDashboard> view(Authentication authentication,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) ProjectStage stage,
            @RequestParam(defaultValue = "") String category, @RequestParam(defaultValue = "") String product,
            @RequestParam(defaultValue = "false") boolean includeTerminated) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal actor)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        try {
            return responses.success(service.view(actor.userId(), new ProjectDashboardFilter(YearMonth.parse(from),
                    YearMonth.parse(to), stage, category, product, includeTerminated)));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_ATTRIBUTION_FILTER);
        }
    }

    @GetMapping("/activities")
    public ApiResponse<List<ProjectDashboardActivity>> activities(Authentication authentication,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) ProjectStage stage,
            @RequestParam(defaultValue = "false") boolean includeTerminated) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal actor)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        try {
            return responses.success(service.activities(actor.userId(), new ProjectDashboardFilter(YearMonth.parse(from),
                    YearMonth.parse(to), stage, "", "", includeTerminated)));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_ATTRIBUTION_FILTER);
        }
    }
}
