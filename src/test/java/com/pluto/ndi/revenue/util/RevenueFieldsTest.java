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
        result.put("三化分类", "数字化");
        result.put("产品名称", "验收产品");
        result.putIfAbsent("费用账期", "202610");
        result.putIfAbsent("业务号码/项目编号", "000042");
        result.put("自然客户名称", "验收自然客户");
        return result;
    }

    @Test
    void normalizesMissingAndPlaceholderClauseNumbersToEmpty() {
        var input = source();
        input.put("条款编号", "  --  ");
        var result = fields.normalize(input);
        assertThat(result.clauseNo()).isEmpty();
        assertThat(result.fields()).containsEntry("条款编号", "");
        assertThat(RevenueImport.REQUIRED)
                .containsExactlyInAnyOrder("月账期", "费用账期", "省份名称",
                        "业务号码/项目编号", "产品名称", "条款金额", RevenueFields.SOURCE_AMOUNT, "三化分类");
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
        assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("合同编号必填");
        for (var category : new String[] {"数字化", "智能化"}) {
            input.put("三化分类", category);
            assertThatThrownBy(() -> fields.normalizeImport(input)).hasMessageContaining("合同编号必填");
        }
        input.put("三化分类", "网络化");
        for (var value : new String[] {"", "   ", "--", "—", "N/A"}) {
            input.put("合同编号", value);
            assertThat(fields.normalizeImport(input).contractNo()).isEmpty();
        }
        input.put("合同编号", "00042");
        assertThat(fields.normalizeImport(input).contractNo()).isEqualTo("00042");
        assertThat(RevenueImport.rules()).anySatisfy(rule -> {
            assertThat(rule.name()).isEqualTo("合同编号");
            assertThat(rule.requirement()).isEqualTo("CONDITIONAL");
        });
        var missingAmount = source();
        missingAmount.put("条款金额", "--");
        assertThatThrownBy(() -> fields.normalize(missingAmount)).hasMessageContaining("条款金额必填");
    }

    @Test
    void requiredFieldsRejectMissingValuesAndSupportLegacyOrganizationAlias() {
        var input = source();
        assertThat(fields.normalize(input).fields()).containsEntry("省份名称", "具体组织")
                .containsEntry("费用账期", "2026-10");
        for (var field : RevenueFields.REQUIRED) {
            for (var blank : new String[] {"", "   ", "—", "N/A"}) {
                var missing = new LinkedHashMap<>(input);
                missing.put(field, blank);
                assertThatThrownBy(() -> fields.normalize(missing)).hasMessageContaining(field + "必填");
            }
        }
    }

    @Test
    void importsHalfUpAfterParsingAndEnforcesRequiredSourceAmounts() {
        for (var field : new String[] {"条款金额", RevenueFields.SOURCE_AMOUNT, RevenueImport.AMOUNT}) {
            assertThat(RevenueFields.importAmount("1,012.345", field)).isEqualByComparingTo("1012.35");
            assertThat(RevenueFields.importAmount("-12.345", field)).isEqualByComparingTo("-12.35");
            assertThat(RevenueFields.importAmount("0.005", field)).isEqualByComparingTo("0.01");
            assertThat(RevenueFields.importAmount("-0.005", field)).isEqualByComparingTo("-0.01");
            assertThat(RevenueFields.importAmount("-0.0049", field)).isEqualByComparingTo("0.00");
            assertThat(RevenueFields.importAmount("1E-2147483647", field)).isEqualByComparingTo("0.00");
            assertThatThrownBy(() -> RevenueFields.importAmount("-9999999999999999.995", field))
                    .hasMessageContaining("金额超出范围");
            assertThatThrownBy(() -> RevenueFields.importAmount("abc", field)).hasMessageContaining("须为有效金额");
        }
        for (var field : new String[] {"条款金额", RevenueFields.SOURCE_AMOUNT}) {
            var input = source();
            input.put(field, "");
            assertThatThrownBy(() -> fields.normalizeImport(input)).hasMessageContaining(field + "必填");
            input.put(field, "-12.345");
            assertThat(fields.normalizeImport(input).fields()).containsEntry(field, "-12.35");
            assertThatThrownBy(() -> fields.normalize(input)).hasMessageContaining("最多两位小数");
        }
        assertThatThrownBy(() -> RevenueFields.amount("-1", "条款金额", true)).hasMessageContaining("超出范围");
    }

    @Test
    void optionalIdentityFieldsKeepMissingWhitespaceAndPlaceholderAsEmpty() {
        var input = source();
        for (var value : new String[] {"", "   ", "--", "  --  ", "\t--\n"}) {
            for (var field : new String[] {"条款编号", "自然客户名称", "产品编码"})
                input.put(field, value);
            var result = fields.normalize(input);
            assertThat(result.clauseNo()).isEmpty();
            for (var field : new String[] {"条款编号", "自然客户名称", "产品编码"})
                assertThat(result.fields().get(field)).isEmpty();
        }
    }
}
