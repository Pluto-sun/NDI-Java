package com.pluto.ndi.projects.domain;

import java.util.Optional;

/** 三化类别的稳定代码。 */
public enum ProjectCategory {
    NETWORK("网络化"),
    GEWU("格物"),
    BIG_DATA("大数据"),
    WANGWU("万悟");

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
