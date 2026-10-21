package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluto.ndi.projects.domain.ProjectNarrative;
import org.junit.jupiter.api.Test;

class ProjectNarrativeTextTest {
    @Test
    void defaultsOriginalConstructionAndTrimsOptionalText() {
        assertThat(ProjectNarrativeText.normalize(null, "客户需求"))
                .isEqualTo(new ProjectNarrative("客户需求", null, null));
        assertThat(ProjectNarrativeText.normalize(
                new ProjectNarrative(" 原始建设 ", " 产品方案 ", " 备注 "), "客户需求"))
                .isEqualTo(new ProjectNarrative("原始建设", "产品方案", "备注"));
    }

    @Test
    void rejectsOverlongExplicitContent() {
        assertThatThrownBy(() -> ProjectNarrativeText.normalize(
                new ProjectNarrative("x".repeat(4001), null, null), "客户需求"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectNarrativeText.normalize(
                new ProjectNarrative(null, null, "x".repeat(10001)), "客户需求"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
