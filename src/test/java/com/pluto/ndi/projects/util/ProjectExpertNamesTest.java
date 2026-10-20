package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProjectExpertNamesTest {
    @Test
    void parsesOptionalNamesAndRejectsDuplicates() {
        assertThat(ProjectExpertNames.parse("张专家， 李专家\n王专家"))
                .containsExactly("张专家", "李专家", "王专家");
        assertThat(ProjectExpertNames.normalize(null)).isEmpty();
        assertThatThrownBy(() -> ProjectExpertNames.parse("张专家, 张专家"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectExpertNames.normalize(List.of("x".repeat(101))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
