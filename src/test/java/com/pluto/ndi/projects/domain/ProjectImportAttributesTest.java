package com.pluto.ndi.projects.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProjectImportAttributesTest {
    @Test
    void defaultsBlankFieldsAndRetainsUnenumeratedCodes() {
        assertThat(new ProjectImportAttributes(null, " ")).isEqualTo(new ProjectImportAttributes("NORMAL", null));
        assertThat(new ProjectImportAttributes(" KEY ", " 行业专家 "))
                .isEqualTo(new ProjectImportAttributes("KEY", "行业专家"));
        assertThat(new ProjectImportAttributes("自定义代码", null).importanceCode()).isEqualTo("自定义代码");
    }

    @Test
    void validatesStorageBoundariesWithoutTruncatingValues() {
        assertThat(new ProjectImportAttributes("a".repeat(30), "专".repeat(500)).supportIndustryExpert()).hasSize(500);
        assertThatThrownBy(() -> new ProjectImportAttributes("a".repeat(31), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("重要度");
        assertThatThrownBy(() -> new ProjectImportAttributes(null, "专".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("支撑行业专家");
    }
}
