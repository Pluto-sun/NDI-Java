package com.pluto.ndi.projects.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.pluto.ndi.shared.web.BusinessException;

/** 区间总览、趋势与地图共用的所属月份筛选规则。 */
public record ProjectAttributionFilter(YearMonth from, YearMonth to, boolean includeTerminated,
        List<ProjectStage> stages, List<UUID> provinceIds, List<UUID> cityIds) {
    public ProjectAttributionFilter {
        if (from == null || to == null || from.getYear() < 1000 || to.getYear() > 9999
                || from.isAfter(to) || from.plusMonths(35).isBefore(to)) invalid();
        stages = stages == null ? List.of() : stages;
        if (stages.size() > ProjectStage.values().length || stages.stream().anyMatch(Objects::isNull)
                || stages.stream().distinct().count() != stages.size()
                || !includeTerminated && stages.contains(ProjectStage.TERMINATED)) invalid();
        stages = List.copyOf(stages);
        provinceIds = ids(provinceIds);
        cityIds = ids(cityIds);
    }
    private static List<UUID> ids(List<UUID> values) {
        if (values == null) return List.of();
        if (values.size() > 500 || values.stream().anyMatch(Objects::isNull)) invalid();
        return values.stream().distinct().toList();
    }
    private static void invalid() { throw new BusinessException(ProjectErrorCode.INVALID_ATTRIBUTION_FILTER); }
}
