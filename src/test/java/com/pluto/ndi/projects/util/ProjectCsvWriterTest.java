package com.pluto.ndi.projects.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.projects.service.ProjectSummary;
import org.junit.jupiter.api.Test;

class ProjectCsvWriterTest {
    @Test
    void selectedExtraColumnsRespectOrderAndProtectAllText() {
        var project = new ProjectSummary(UUID.randomUUID(), "NDI-1", "未导出名称", "客户", "C25",
                BigDecimal.TEN, "SOLUTION_EXCHANGE", List.of(), Instant.parse("2026-01-01T00:00:00Z"));
        var row = new com.pluto.ndi.projects.service.ProjectExportRow(project, java.util.Map.of(
                com.pluto.ndi.projects.domain.ProjectExportField.PROVINCE, "=省分",
                com.pluto.ndi.projects.domain.ProjectExportField.CITY_MANAGER, "@经理",
                com.pluto.ndi.projects.domain.ProjectExportField.SUPPORT_EXPERTS, "\t专家甲、专家\"乙"));
        var fields = com.pluto.ndi.projects.domain.ProjectExportField.select(List.of("productSupportExpertNames", "cityAccountManagerName", "province"));
        var csv = new String(ProjectCsvWriter.writeRows(List.of(row), fields), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF产品单位支撑专家,地市客户经理,省分\r\n")
                .contains("'\t专家甲、专家\"\"乙", "'@经理", "'=省分").doesNotContain("未导出名称");
    }

    @Test
    void quotesUserTextAndPreventsFormulaExecution() {
        var project = new ProjectSummary(UUID.randomUUID(), "NDI-1", " =1+1", "客户\"甲,乙",
                "C25", new BigDecimal("100.00"), "SOLUTION_EXCHANGE",
                List.of(new ProjectSummary.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                Instant.parse("2026-10-20T00:00:00Z"));

        var csv = new String(ProjectCsvWriter.write(List.of(project)), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFF项目编号,");
        assertThat(csv).contains("\"' =1+1\"");
        assertThat(csv).contains("\"客户\"\"甲,乙\"");
        assertThat(csv).contains("\"60.00\",\"\",\"\",\"\"");
        assertThat(csv).endsWith("\r\n");
    }
}
