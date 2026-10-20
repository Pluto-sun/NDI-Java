package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectPlanningFieldsTest {
    @Test
    void acceptsMonthAndFullDateButStoresMonthPrecision() {
        assertThat(ProjectPlanningFields.parseBidMonth("2026-10")).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ProjectPlanningFields.parseBidMonth("2026/10/20")).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ProjectPlanningFields.parseBidMonth("")).isNull();
        assertThatThrownBy(() -> ProjectPlanningFields.parseBidMonth("2026-13"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectPlanningFields.parseBidMonth("2026-02-30"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsStableScenarioCodesAndRecognizedLabelsOnly() {
        assertThat(ProjectPlanningFields.parseScenarios("柔性产线快速换产，ENERGY_CONTROL"))
                .containsExactly("FLEXIBLE_PRODUCTION", "ENERGY_CONTROL");
        assertThat(ProjectPlanningFields.normalizeScenarios(null)).isEmpty();
        assertThatThrownBy(() -> ProjectPlanningFields.normalizeScenarios(
                List.of("ENERGY_CONTROL", "ENERGY_CONTROL"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectPlanningFields.parseScenarios("其他场景"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
