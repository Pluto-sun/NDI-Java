package com.pluto.ndi.projects.service;

import java.util.Locale;
import java.util.UUID;

import com.pluto.ndi.projects.domain.ProjectStage;

/** 项目列表与导出共用的筛选条件。 */
public record ProjectQuery(
        String projectCode,
        String projectName,
        ProjectStage stage,
        String industryCode,
        UUID provinceId,
        UUID cityId,
        boolean includeTerminated
) {

    public ProjectQuery {
        projectCode = normalize(projectCode, true);
        projectName = normalize(projectName, false);
        industryCode = normalize(industryCode, true);
    }

    public static ProjectQuery all() {
        return new ProjectQuery(null, null, null, null, null, null, true);
    }

    private static String normalize(String value, boolean uppercase) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        return uppercase ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
    }
}
