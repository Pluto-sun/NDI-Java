package com.pluto.ndi.projects.domain;

import java.util.List;
import java.util.UUID;

/** 查重依据随项目名称及候选集合变化，旧确认不能自动套用于新结果。 */
public record ProjectImportNameReview(
        UUID provinceId,
        UUID cityId,
        String level,
        double score,
        String fingerprint,
        List<String> evidence,
        List<Match> matches,
        boolean confirmed,
        String reason) {
    public record Match(
            UUID projectId,
            String projectCode,
            String projectName,
            Integer sourceRowNumber,
            double score) {}

    public boolean accepted() {
        return "NONE".equals(level)
                || confirmed
                        && (!"STRONG".equals(level)
                                || reason != null && reason.strip().length() >= 5);
    }
}
