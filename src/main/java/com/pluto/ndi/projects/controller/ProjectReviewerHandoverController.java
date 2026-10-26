package com.pluto.ndi.projects.controller;

import com.pluto.ndi.projects.service.ProjectReviewerHandoverService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviewer-pending-handover")
public class ProjectReviewerHandoverController {
    private final ProjectReviewerHandoverService service;
    private final ApiResponseFactory responses;

    public ProjectReviewerHandoverController(
            ProjectReviewerHandoverService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<List<ProjectReviewerHandoverService.Pending>> list(Authentication auth) {
        return responses.success(
                service.unassigned(((ActorPrincipal) auth.getPrincipal()).userId()));
    }
}
