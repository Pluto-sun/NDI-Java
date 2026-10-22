package com.pluto.ndi.projects.service;
import java.util.Map;
import com.pluto.ndi.projects.domain.ProjectExportField;

/** 仅用于已授权导出批次的最小数据载体，不直接对外开放查询。 */
public record ProjectExportRow(ProjectSummary project, Map<ProjectExportField, String> extraValues) {
    public ProjectExportRow { extraValues = Map.copyOf(extraValues); }
    public String extra(ProjectExportField field) { return extraValues.getOrDefault(field, ""); }
    public static ProjectExportRow summary(ProjectSummary project) {
        return new ProjectExportRow(project, Map.of());
    }
}
