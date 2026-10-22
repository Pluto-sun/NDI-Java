package com.pluto.ndi.projects.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.service.ProjectAttributionService;
import com.pluto.ndi.projects.service.ProjectAttributionWorkbench;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 旧所属月份口径的独立查询入口，避免与历史月末快照接口混用。 */
@RestController
@RequestMapping("/api/v1/projects/workbench/attribution")
public class ProjectAttributionController {
    private final ProjectAttributionService service;
    private final ApiResponseFactory responses;

    public ProjectAttributionController(ProjectAttributionService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<ProjectAttributionWorkbench> view(Authentication authentication,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean includeTerminated,
            @RequestParam(required = false, name = "stage") List<ProjectStage> stages,
            @RequestParam(required = false) List<UUID> provinceIds,
            @RequestParam(required = false) List<UUID> cityIds) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal actor)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        try {
            return responses.success(service.view(actor.userId(), YearMonth.parse(from), YearMonth.parse(to),
                    includeTerminated, stages, provinceIds, cityIds));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_ATTRIBUTION_FILTER);
        }
    }
}
