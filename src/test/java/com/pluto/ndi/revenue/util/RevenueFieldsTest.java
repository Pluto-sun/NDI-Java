package com.pluto.ndi.revenue.util;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.revenue.domain.RevenueImport;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class RevenueFieldsTest {
    private final RevenueFields fields = new RevenueFields(mock(OrganizationDirectory.class));

    private Map<String, String> source() {
        var result =
                new LinkedHashMap<>(
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
        result.put("自然客户名称", "验收自然客户");
        return result;
    }

    @Test
    void allowsMissingClauseNumberAndPreservesExplicitPlaceholder() {
        var input = source();
        input.put("条款编号", "  --  ");
        var result = fields.normalize(input);
        assertThat(result.clauseNo()).isEqualTo("--");
        assertThat(result.fields()).containsEntry("条款编号", "--");
        assertThat(RevenueImport.REQUIRED)
                .containsExactlyInAnyOrder("月账期", "自然客户名称", "条款金额", RevenueFields.SOURCE_AMOUNT);
        assertThat(RevenueImport.rules().stream().filter(r -> r.name().equals("条款编号")).findFirst())
                .isPresent();
        for (String blank : new String[] {"", "   "}) {
            input.put("条款编号", blank);
            assertThat(fields.normalize(input).clauseNo()).isEmpty();
        }
        input.remove("条款编号");
        assertThat(fields.normalize(input).clauseNo()).isEmpty();
    }

    @Test
    void keepsExistingPlaceholderRulesForOtherFields() {
        var input = source();
        input.put("项目名称", "--");
        assertThat(fields.normalize(input).fields()).containsEntry("项目名称", "");
        input.put("合同编号", "--");
        assertThat(fields.normalize(input).contractNo()).isEmpty();
        var missingAmount = source();
        missingAmount.put("条款金额", "--");
        assertThatThrownBy(() -> fields.normalize(missingAmount)).hasMessageContaining("条款金额必填");
    }

    @Test
    void onlyFourBusinessFieldsAreRequiredAndUnassignedDoesNotBecomeOrganization() {
        var input =
                new LinkedHashMap<>(
                        Map.of(
                                "月账期",
                                "202611",
                                "自然客户名称",
                                "客户",
                                "条款金额",
                                "10",
                                RevenueFields.SOURCE_AMOUNT,
                                "0"));
        var normalized = fields.normalize(input);
        assertThat(normalized.provinceId()).isNull();
        assertThat(normalized.organization()).isFalse();
        assertThat(normalized.contractNo()).isEmpty();
        assertThat(normalized.clauseNo()).isEmpty();
        for (var field : RevenueFields.REQUIRED) {
            var missing = new LinkedHashMap<>(input);
            missing.remove(field);
            assertThatThrownBy(() -> fields.normalize(missing)).hasMessageContaining(field + "必填");
        }
        input.put("省份名称", "具体组织");
        assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("组织名称");
    }
}
