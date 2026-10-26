package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectNameMatcherTest {

    @Test
    void normalizesFullWidthLettersAndPunctuation() {
        assertThat(ProjectNameMatcher.standard("ＡＢＣ－智慧工厂（一期）项目")).isEqualTo("abc智慧工厂1期项目");
        assertThat(ProjectNameMatcher.similarity("ＡＢＣ－智慧工厂（一期）项目", "ABC 智慧工厂一期项目")).isEqualTo(100);
    }

    @Test
    void normalizesYearDigitsVersionAndGenericBoundariesWithoutLosingQuantityWords() {
        assertThat(ProjectNameMatcher.core("建设ＡＢＣ　二零二四 V２．０ 平台项目"))
                .isEqualTo(ProjectNameMatcher.core("abc 2024 v2.0系统"));
        assertThat(ProjectNameMatcher.standard("一万零二个传感器")).isEqualTo("一万零二个传感器");
        assertThat(ProjectNameMatcher.score("frog", "fog"))
                .isCloseTo(0.8375, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(ProjectNameMatcher.standard("v2.0"))
                .isNotEqualTo(ProjectNameMatcher.standard("v20"));
        assertThat(ProjectNameMatcher.score("a", "b")).isZero();
        assertThat(ProjectNameMatcher.score("", "")).isZero();
    }

    @Test
    void unrelatedNamesAreNotCandidates() {
        assertThat(ProjectNameMatcher.similarity("华东智能产线建设", "西南数据中心扩容")).isLessThan(75);
    }
}
