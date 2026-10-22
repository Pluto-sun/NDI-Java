package com.pluto.ndi.projects.domain;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.pluto.ndi.shared.web.BusinessException;

class ProjectExportFieldTest {
    @Test void defaultsRemainTheElevenSummaryColumns() {
        assertThat(ProjectExportField.defaultCodes()).hasSize(11).startsWith("projectCode", "projectName")
                .endsWith("createdAt");
        assertThat(ProjectExportField.values()).hasSize(ProjectExportField.MAX_FIELDS);
        assertThat(ProjectExportField.select(null)).extracting(ProjectExportField::code).containsExactlyElementsOf(ProjectExportField.defaultCodes());
    }
    @Test void preservesRequestedOrderWithoutDuplicatesOrUnknownFields() {
        assertThat(ProjectExportField.select(List.of("province", "projectName")))
                .containsExactly(ProjectExportField.PROVINCE, ProjectExportField.PROJECT_NAME);
        for (var values : List.of(List.<String>of(), List.of("unknown"), List.of("province", "province")))
            assertThatThrownBy(() -> ProjectExportField.select(values)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ProjectExportField.select(java.util.Arrays.asList((String) null))).isInstanceOf(BusinessException.class);
    }
}
