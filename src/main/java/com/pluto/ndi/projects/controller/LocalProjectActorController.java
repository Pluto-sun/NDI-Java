package com.pluto.ndi.projects.controller;

import java.util.List;

import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.projects.service.ProjectContextService;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供本机开发联调选择现有账号；生产环境不注册此控制器。 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/projects/dev")
public class LocalProjectActorController {

    private final ProjectContextService service;
    private final ApiResponseFactory responses;

    public LocalProjectActorController(ProjectContextService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/actors")
    public ApiResponse<List<ProjectUser>> actors() {
        return responses.success(service.localActors());
    }
}
