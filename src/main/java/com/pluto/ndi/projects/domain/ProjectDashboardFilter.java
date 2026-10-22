package com.pluto.ndi.projects.domain;

import java.time.YearMonth;
import java.util.List;
import com.pluto.ndi.shared.web.BusinessException;

/** 驾驶舱统一筛选；产品归属按勾选关系判断，零金额项目仍然参与计数。 */
public record ProjectDashboardFilter(YearMonth from, YearMonth to, ProjectStage stage,
        String category, String product, boolean includeTerminated) {
    public ProjectDashboardFilter {
        new ProjectAttributionFilter(from, to, includeTerminated,
                stage == null ? List.of() : List.of(stage), List.of(), List.of());
        category = category == null ? "" : category;
        product = product == null ? "" : product;
        if (!List.of("", "NETWORK", "DIGITAL", "INTELLIGENT").contains(category)
                || !List.of("", "NETWORK", "GEWU", "BIG_DATA", "WANGWU").contains(product)) {
            throw new BusinessException(ProjectErrorCode.INVALID_ATTRIBUTION_FILTER);
        }
    }
}
