package com.pluto.ndi.projects.util;

import java.util.*;

/** 区分列映射完整性与行内值校验，条件必填只针对当前文件实际涉及的业务。 */
public final class ProjectImportMappingRules {
    public static final Set<String> REQUIRED = Set.copyOf(ProjectExcelReader.requiredHeaders());

    private ProjectImportMappingRules() {}

    public static List<String> missing(
            Map<String, String> mapping, List<ProjectExcelReader.ExcelRow> rows) {
        var missing = new TreeSet<String>();
        REQUIRED.stream().filter(field -> !mapped(mapping, field)).forEach(missing::add);
        if (ProjectImportAmounts.fields().stream().noneMatch(field -> mapped(mapping, field)))
            missing.add("至少一项三化预计金额");
        for (var row : rows) {
            String stage = row.get("项目阶段");
            if ("计收".equals(stage)
                    || Set.of("签约", "已终止").contains(stage)
                            && (!row.get("合同编号").isBlank() || !row.get("签约金额（万）").isBlank()))
                for (var field : List.of("合同编号", "签约金额（万）"))
                    if (!mapped(mapping, field)) missing.add(field);
        }
        return List.copyOf(missing);
    }

    private static boolean mapped(Map<String, String> mapping, String field) {
        return mapping.get(field) != null && !mapping.get(field).isBlank();
    }
}
