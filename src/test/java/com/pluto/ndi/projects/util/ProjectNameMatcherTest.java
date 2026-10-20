package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectNameMatcherTest {

    @Test
    void normalizesFullWidthLettersAndPunctuation() {
        assertThat(ProjectNameMatcher.standard("ＡＢＣ－智慧工厂（一期）项目"))
                .isEqualTo("abc智慧工厂一期项目");
        assertThat(ProjectNameMatcher.similarity("ＡＢＣ－智慧工厂（一期）项目", "ABC 智慧工厂一期项目"))
                .isEqualTo(100);
    }

    @Test
    void unrelatedNamesAreNotCandidates() {
        assertThat(ProjectNameMatcher.similarity("华东智能产线建设", "西南数据中心扩容"))
                .isLessThan(75);
    }
}
