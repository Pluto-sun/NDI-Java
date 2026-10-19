package com.pluto.ndi.projects.domain;

import java.util.Optional;

/** 三化类别的稳定代码。 */
public enum ProjectCategory {
    NETWORK,
    GEWU,
    BIG_DATA,
    WANGWU;

    public static Optional<ProjectCategory> fromCode(String code) {
        try {
            return Optional.of(ProjectCategory.valueOf(code));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }
}
