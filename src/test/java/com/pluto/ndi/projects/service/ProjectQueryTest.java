package com.pluto.ndi.projects.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectQueryTest {
    @Test
    void normalizesNewFiltersWithoutTreatingLiteralWildcardsAsBlank() {
        var query = new ProjectQuery(null, null, null, null, null, null, true,
                " ds_sign ", " 负责人 ", " %_ ", " 专家 ", " 编号 ");
        assertThat(query.signingSubjectCode()).isEqualTo("DS_SIGN");
        assertThat(query.provinceLeaderName()).isEqualTo("负责人");
        assertThat(query.cityAccountManagerName()).isEqualTo("%_");
        assertThat(query.productSupportExpertName()).isEqualTo("专家");
        assertThat(query.keyword()).isEqualTo("编号");
    }

    @Test
    void readsPreviouslyPersistedExportQueriesWithNoAdvancedFields() throws Exception {
        var query = new ObjectMapper().readValue("{\"projectName\":\"旧任务\",\"includeTerminated\":true}", ProjectQuery.class);
        assertThat(query.projectName()).isEqualTo("旧任务");
        assertThat(query.includeTerminated()).isTrue();
        assertThat(query.signingSubjectCode()).isNull();
        assertThat(query.keyword()).isNull();
    }

    @Test
    void rejectsUnknownSigningSubjectsRatherThanIgnoringThem() {
        assertThatThrownBy(() -> new ProjectQuery(null, null, null, null, null, null, true,
                "INVALID", null, null, null, null)).isInstanceOf(BusinessException.class);
    }
}
