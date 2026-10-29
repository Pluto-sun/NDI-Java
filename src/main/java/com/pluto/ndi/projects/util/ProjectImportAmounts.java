package com.pluto.ndi.projects.util;

import com.pluto.ndi.projects.domain.ProjectCategory;

import java.util.List;
import java.util.Map;

/** 项目导入仅以分类金额列确定类别，零金额同样表示存在该类别。 */
public final class ProjectImportAmounts {
    public static final Map<String, String> HEADERS =
            Map.of(
                    "NETWORK",
                    "网络化预计金额（万）",
                    "GEWU",
                    "格物预计金额（万）",
                    "BIG_DATA",
                    "大数据预计金额（万）",
                    "WANGWU",
                    "万悟预计金额（万）");

    private ProjectImportAmounts() {}

    public static List<String> fields() {
        return java.util.Arrays.stream(ProjectCategory.values())
                .map(category -> HEADERS.get(category.name()))
                .toList();
    }
}
