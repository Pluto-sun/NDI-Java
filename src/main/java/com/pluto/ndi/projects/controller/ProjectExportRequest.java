package com.pluto.ndi.projects.controller;

import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.service.ProjectQuery;
import jakarta.validation.constraints.Size;

/** 与项目列表使用相同筛选字段的导出请求。 */
public record ProjectExportRequest(
        @Size(max = 40, message = "项目编号不得超过40个字符") String projectCode,
        @Size(max = 200, message = "项目名称不得超过200个字符") String projectName,
        ProjectStage stage,
        @Size(max = 3, message = "行业代码不得超过3个字符") String industryCode,
        UUID provinceId,
        UUID cityId,
        Boolean includeTerminated
) {
    public ProjectQuery toQuery() {
        return new ProjectQuery(projectCode, projectName, stage, industryCode,
                provinceId, cityId, includeTerminated == null || includeTerminated);
    }
}
