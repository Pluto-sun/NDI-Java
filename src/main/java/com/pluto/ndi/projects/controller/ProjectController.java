package com.pluto.ndi.projects.controller;

import java.util.UUID;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.service.ProjectQuery;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.projects.service.ProjectSummary;
import com.pluto.ndi.projects.service.ProjectDetailSummary;
import com.pluto.ndi.projects.service.ProjectProfileService;
import com.pluto.ndi.projects.service.ProjectDuplicateService;
import com.pluto.ndi.projects.service.ProjectDuplicateCheck;
import com.pluto.ndi.shared.web.ApiResponse;
import com.pluto.ndi.shared.web.ApiResponseFactory;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.PageResponse;
import com.pluto.ndi.shared.security.ActorPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 项目主档的创建、查询与普通信息维护接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectProfileService profileService;
    private final ProjectDuplicateService duplicateService;
    private final ApiResponseFactory responseFactory;

    public ProjectController(ProjectService projectService, ProjectProfileService profileService,
                             ProjectDuplicateService duplicateService,
                             ApiResponseFactory responseFactory) {
        this.projectService = projectService;
        this.profileService = profileService;
        this.duplicateService = duplicateService;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/duplicate-check")
    public ApiResponse<ProjectDuplicateCheck> duplicateCheck(Authentication authentication,
            @RequestParam UUID cityId,
            @RequestParam @Size(min = 2, max = 200) String projectName) {
        return responseFactory.success(duplicateService.check(actorId(authentication), cityId, projectName));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectSummary> createProject(
            Authentication authentication,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return responseFactory.success(projectService.createProject(actorId(authentication), request.toCommand()));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectSummary>> findProjects(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不得小于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量不得小于1")
            @Max(value = 100, message = "每页数量不得超过100") int size,
            @RequestParam(required = false) @Size(max = 40, message = "项目编号不得超过40个字符") String projectCode,
            @RequestParam(required = false) @Size(max = 200, message = "项目名称不得超过200个字符") String projectName,
            @RequestParam(required = false) ProjectStage stage,
            @RequestParam(required = false) @Size(max = 3, message = "行业代码不得超过3个字符") String industryCode,
            @RequestParam(required = false) UUID provinceId,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) @Size(max = 20, message = "签约主体代码不得超过20个字符") String signingSubjectCode,
            @RequestParam(required = false) @Size(max = 100, message = "省分负责人姓名不得超过100个字符") String provinceLeaderName,
            @RequestParam(required = false) @Size(max = 100, message = "客户经理姓名不得超过100个字符") String cityAccountManagerName,
            @RequestParam(required = false) @Size(max = 100, message = "支撑专家姓名不得超过100个字符") String productSupportExpertName,
            @RequestParam(required = false) @Size(max = 200, message = "关键字不得超过200个字符") String keyword,
            @RequestParam(required = false) @Size(max = 100) String responsiblePersonName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate opportunityDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate opportunityDateEnd,
            @RequestParam(defaultValue = "true") boolean includeTerminated
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var query = new ProjectQuery(projectCode, projectName, stage, industryCode,
                provinceId, cityId, includeTerminated, signingSubjectCode, provinceLeaderName,
                cityAccountManagerName, productSupportExpertName, keyword, opportunityDateStart, opportunityDateEnd,responsiblePersonName);
        return responseFactory.success(PageResponse.from(projectService.findProjects(actorId(authentication), query, pageable)));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDetailSummary> findDetail(
            Authentication authentication, @PathVariable UUID projectId
    ) {
        return responseFactory.success(projectService.findDetail(actorId(authentication), projectId));
    }

    @PutMapping("/{projectId}/profile")
    public ApiResponse<ProjectDetailSummary> updateProfile(
            Authentication authentication, @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectProfileRequest request
    ) {
        return responseFactory.success(profileService.update(actorId(authentication), projectId, request.toCommand()));
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ActorPrincipal principal)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return principal.userId();
    }
}
