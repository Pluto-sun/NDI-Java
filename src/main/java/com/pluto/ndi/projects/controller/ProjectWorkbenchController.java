package com.pluto.ndi.projects.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectMonthlyRevision;
import com.pluto.ndi.projects.service.ProjectWorkbenchBreakdown;
import com.pluto.ndi.projects.service.ProjectWorkbenchOverview;
import com.pluto.ndi.projects.service.ProjectWorkbenchService;
import com.pluto.ndi.projects.service.ProjectActivityService;
import com.pluto.ndi.projects.service.ProjectRecentActivity;
import com.pluto.ndi.projects.service.ProjectMapService;
import com.pluto.ndi.projects.service.ProjectMapView;
import com.pluto.ndi.projects.service.ProjectMapBoundaryClient;
import com.pluto.ndi.projects.service.ProjectMonthlySnapshotService;
import com.pluto.ndi.projects.service.ProjectMonthlyTrendPoint;
import com.pluto.ndi.projects.service.ProjectStageTrendPoint;
import com.pluto.ndi.projects.service.ProjectMonthlyWorkbench;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目管理模块专属工作台接口。 */
@RestController
@RequestMapping("/api/v1/projects/workbench")
public class ProjectWorkbenchController {

    private final ProjectWorkbenchService service;
    private final ProjectActivityService activities;
    private final ProjectMonthlySnapshotService monthlySnapshots;
    private final ProjectMapService maps;
    private final ProjectMapBoundaryClient boundaries;
    private final ApiResponseFactory responseFactory;

    public ProjectWorkbenchController(ProjectWorkbenchService service, ProjectActivityService activities,
            ProjectMonthlySnapshotService monthlySnapshots, ProjectMapService maps,
            ProjectMapBoundaryClient boundaries, ApiResponseFactory responseFactory) {
        this.service = service;
        this.activities = activities;
        this.monthlySnapshots = monthlySnapshots;
        this.maps = maps;
        this.boundaries = boundaries;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/recent-activities")
    public ApiResponse<List<ProjectRecentActivity>> recentActivities(Authentication authentication) {
        return responseFactory.success(activities.recent(actorId(authentication)));
    }

    @GetMapping("/map")
    public ApiResponse<ProjectMapView> map(Authentication authentication,
            @RequestParam(required = false) UUID provinceId,
            @RequestParam(defaultValue = "false") boolean includeTerminated,
            @RequestParam(required = false) ProjectStage stage) {
        return responseFactory.success(maps.current(actorId(authentication), provinceId, includeTerminated, stage));
    }

    @GetMapping("/map/boundaries/{adcode}")
    public ResponseEntity<byte[]> boundary(Authentication authentication, @PathVariable String adcode) {
        maps.requireBoundaryAccess(actorId(authentication), adcode);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(boundaries.fetch(adcode));
    }

    @GetMapping("/overview")
    public ApiResponse<ProjectWorkbenchOverview> overview(
            Authentication authentication, @RequestParam(defaultValue = "false") boolean includeTerminated
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return responseFactory.success(service.overview(principal.userId(), includeTerminated));
    }

    @GetMapping("/breakdown")
    public ApiResponse<ProjectWorkbenchBreakdown> breakdown(
            Authentication authentication, @RequestParam(defaultValue = "false") boolean includeTerminated
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return responseFactory.success(service.breakdown(principal.userId(), includeTerminated));
    }

    @GetMapping("/history")
    public ApiResponse<ProjectMonthlyWorkbench> history(Authentication authentication,
            @RequestParam String month, @RequestParam(defaultValue = "false") boolean includeTerminated) {
        return responseFactory.success(monthlySnapshots.workbench(actorId(authentication), parseMonth(month),
                includeTerminated));
    }

    @GetMapping("/trend")
    public ApiResponse<List<ProjectMonthlyTrendPoint>> trend(Authentication authentication,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean includeTerminated) {
        return responseFactory.success(monthlySnapshots.trend(actorId(authentication),
                parseMonth(from), parseMonth(to), includeTerminated));
    }

    @GetMapping("/stage-trend")
    public ApiResponse<List<ProjectStageTrendPoint>> stageTrend(Authentication authentication,
            @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "false") boolean includeTerminated,
            @RequestParam(required = false, name = "stage") List<ProjectStage> stages) {
        return responseFactory.success(monthlySnapshots.stageTrend(actorId(authentication),
                parseMonth(from), parseMonth(to), includeTerminated, stages));
    }

    @GetMapping("/history/{projectId}/revisions")
    public ApiResponse<List<ProjectMonthlyRevision>> revisions(Authentication authentication,
            @PathVariable UUID projectId, @RequestParam String month) {
        return responseFactory.success(monthlySnapshots.revisions(
                actorId(authentication), projectId, parseMonth(month)));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }

    private YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_SNAPSHOT_MONTH);
        }
    }
}
