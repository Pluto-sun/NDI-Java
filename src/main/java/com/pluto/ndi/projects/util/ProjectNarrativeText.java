package com.pluto.ndi.projects.util;

import com.pluto.ndi.projects.domain.ProjectNarrative;

/** 项目补充文本的统一长度约束；空白值不作为有效内容保存。 */
public final class ProjectNarrativeText {
    private ProjectNarrativeText() { }

    public static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        var normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("项目补充文本超过允许长度");
        }
        return normalized;
    }

    public static String construction(String raw, String customerNeed) {
        var normalized = optional(raw, 4_000);
        return normalized == null ? customerNeed : normalized;
    }

    public static ProjectNarrative normalize(ProjectNarrative narrative, String customerNeed) {
        if (narrative == null) narrative = new ProjectNarrative(null, null, null);
        return new ProjectNarrative(
                construction(narrative.constructionContentRaw(), customerNeed),
                optional(narrative.productPromotionContent(), 4_000),
                optional(narrative.remark(), 10_000));
    }
}
