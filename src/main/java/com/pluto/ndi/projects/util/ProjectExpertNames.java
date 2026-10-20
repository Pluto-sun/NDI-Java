package com.pluto.ndi.projects.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 支撑专家仅作责任信息，不授予项目访问或审批权限。 */
public final class ProjectExpertNames {
    private ProjectExpertNames() { }

    public static List<String> normalize(List<String> names) {
        if (names == null) return List.of();
        var result = new ArrayList<String>();
        Set<String> seen = new HashSet<>();
        for (var name : names) {
            if (name == null || name.isBlank()) continue;
            var normalized = name.trim();
            if (normalized.length() > 100 || !seen.add(normalized)) {
                throw new IllegalArgumentException("支撑专家姓名不能超过100字或重复");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    public static List<String> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        return normalize(List.of(value.split("[,，、;；\\n]+")));
    }
}
