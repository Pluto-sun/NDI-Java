package com.pluto.ndi.revenue.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.revenue.domain.RevenueImport;
import com.pluto.ndi.shared.web.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class RevenueFieldsTest {
    private final RevenueFields fields = new RevenueFields(mock(OrganizationDirectory.class));

    private Map<String, String> source() {
        return new LinkedHashMap<>(
                Map.of(
                        "月账期",
                        "202610",
                        "省份名称",
                        "--",
                        "地市/组织名称",
                        "具体组织名称",
                        "合同编号",
                        "HT001",
                        "条款编号",
                        "--",
                        "条款金额",
                        "100",
                        RevenueFields.SOURCE_AMOUNT,
                        "0"));
    }

    @Test
    void preservesExplicitNoClauseNumberAndKeepsItRequired() {
        var input = source();
        input.put("条款编号", "  --  ");
        var result = fields.normalize(input);
        assertThat(result.clauseNo()).isEqualTo("--");
        assertThat(result.fields()).containsEntry("条款编号", "--");
        assertThat(RevenueImport.REQUIRED).contains("条款编号");
        assertThat(RevenueImport.rules().stream().filter(r -> r.name().equals("条款编号")).findFirst())
                .isPresent();
        for (String blank : new String[] {"", "   "}) {
            input.put("条款编号", blank);
            assertThatThrownBy(() -> fields.normalize(input))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("条款编号必填");
        }
        input.remove("条款编号");
        assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("条款编号必填");
    }

    @Test
    void keepsExistingPlaceholderRulesForOtherFields() {
        var input = source();
        input.put("项目名称", "--");
        assertThat(fields.normalize(input).fields()).containsEntry("项目名称", "");
        input.put("合同编号", "--");
        assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("合同编号必填");
        var missingAmount = source();
        missingAmount.put("条款金额", "--");
        assertThatThrownBy(() -> fields.normalize(missingAmount)).hasMessageContaining("条款金额必填");
    }
}
