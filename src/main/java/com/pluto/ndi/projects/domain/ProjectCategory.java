package com.pluto.ndi.projects.domain;

import java.util.Optional;

/** 三化类别的稳定代码。 */
public enum ProjectCategory {
    NETWORK("5G工业专网"),
    GEWU("格物工业互联网平台"),
    BIG_DATA("工业大数据平台"),
    WANGWU("万悟工业智能体平台");

    private final String label;

    ProjectCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<ProjectCategory> fromCode(String code) {
        try {
            return Optional.of(ProjectCategory.valueOf(code));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }
}
