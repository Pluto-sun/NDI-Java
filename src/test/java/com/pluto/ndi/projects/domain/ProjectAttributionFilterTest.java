package com.pluto.ndi.projects.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.pluto.ndi.shared.web.BusinessException;

class ProjectAttributionFilterTest {
    @Test
    void normalizesAndCopiesFiltersForBothStatisticsAndMaps() {
        var stages = new ArrayList<>(List.of(ProjectStage.BIDDING));
        var filter = new ProjectAttributionFilter(YearMonth.of(2024, 1), YearMonth.of(2026, 12), false, stages, null, null);
        stages.clear();
        assertThat(filter.stages()).containsExactly(ProjectStage.BIDDING);
        assertThat(filter.provinceIds()).isEmpty();
        assertThatThrownBy(() -> filter.stages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsTooLongReversedDuplicateAndTerminatedFilters() {
        var from = YearMonth.of(2024, 1);
        for (var stages : List.of(List.of(ProjectStage.BIDDING, ProjectStage.BIDDING), List.of(ProjectStage.TERMINATED))) {
            assertThatThrownBy(() -> new ProjectAttributionFilter(from, from, false, stages, null, null))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> new ProjectAttributionFilter(from, from.plusMonths(36), false, null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new ProjectAttributionFilter(from, from.minusMonths(1), false, null, null, null))
                .isInstanceOf(BusinessException.class);
    }
}
