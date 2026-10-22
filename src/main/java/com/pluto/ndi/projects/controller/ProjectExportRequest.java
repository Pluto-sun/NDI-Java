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
        Boolean includeTerminated,
        @Size(max = 20, message = "签约主体代码不得超过20个字符") String signingSubjectCode,
        @Size(max = 100, message = "省分负责人姓名不得超过100个字符") String provinceLeaderName,
        @Size(max = 100, message = "客户经理姓名不得超过100个字符") String cityAccountManagerName,
        @Size(max = 100, message = "支撑专家姓名不得超过100个字符") String productSupportExpertName,
        @Size(max = 200, message = "关键字不得超过200个字符") String keyword
) {
    public ProjectQuery toQuery() {
        return new ProjectQuery(projectCode, projectName, stage, industryCode,
                provinceId, cityId, includeTerminated == null || includeTerminated,
                signingSubjectCode, provinceLeaderName, cityAccountManagerName, productSupportExpertName, keyword);
    }
}
