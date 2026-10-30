package com.pluto.ndi.revenue.util;

import com.pluto.ndi.organization.service.*;
import com.pluto.ndi.revenue.domain.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Component;

import java.math.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/** 模板、映射与手动创建共用来源字段，金额不进行税额换算。 */
@Component
public class RevenueFields {
    public static final List<String> FIELDS =
            List.of(
                    "月账期",
                    "费用账期",
                    "省份名称",
                    "地市/组织名称",
                    "系统分类",
                    "业务号码/项目编号",
                    "项目名称",
                    "产品线",
                    "产品线名称",
                    "项目经理ID",
                    "项目经理",
                    "合同编号",
                    "条款编号",
                    "合同名称",
                    "审签日期",
                    "条款金额",
                    "客户名称",
                    "自然客户id",
                    "自然客户名称",
                    "产品编码",
                    "产品名称",
                    "三化分类",
                    "一级行业编码",
                    "一级行业名称",
                    "二级行业编码",
                    "二级行业名称",
                    "三级行业编码",
                    "三级行业名称",
                    "四级行业编码",
                    "四级行业名称",
                    "是否工业",
                    "税率",
                    "产品本月计收不含税额/标品含税收入");
    public static final String SOURCE_AMOUNT = "产品本月计收不含税额/标品含税收入";
    public static final Set<String> REQUIRED =
            Set.of("月账期", "省份名称", "合同编号", "条款编号", "条款金额", SOURCE_AMOUNT);
    private final OrganizationDirectory organizations;

    public RevenueFields(OrganizationDirectory organizations) {
        this.organizations = organizations;
    }

    public record Source(
            Map<String, String> fields,
            String month,
            UUID provinceId,
            boolean organization,
            String contractNo,
            String clauseNo,
            BigDecimal clauseAmount,
            BigDecimal sourceAmount) {}

    public Source normalize(Map<String, String> input) {
        if (input == null || !FIELDS.containsAll(input.keySet()))
            throw invalid("来源字段无效，不允许创建时写入认定字段");
        var values = new LinkedHashMap<String, String>();
        for (var field : FIELDS) {
            var value = Objects.toString(input.get(field), "").strip();
            if (value.length() > 2000) throw invalid(field + "不得超过2000字");
            values.put(field, value.equals("--") && !field.equals("省份名称") ? "" : value);
        }
        for (var field : FIELDS)
            if (REQUIRED.contains(field) && values.get(field).isBlank())
                throw invalid(field + "必填");
        String month = month(values.get("月账期"));
        values.put("月账期", month);
        if (!values.get("费用账期").isBlank()) values.put("费用账期", month(values.get("费用账期")));
        String province = values.get("省份名称");
        boolean organization = Set.of("--", "具体组织").contains(province);
        UUID provinceId = null;
        if (organization) {
            values.put("省份名称", "具体组织");
            if (values.get("地市/组织名称").isBlank()) throw invalid("具体组织必须填写地市/组织名称全称");
        } else {
            var found =
                    organizations.findEnabledProvinces().stream()
                            .filter(
                                    p ->
                                            p.name().equals(province)
                                                    || shortProvince(p.name()).equals(province))
                            .toList();
            if (found.size() != 1) throw invalid("省份名称未匹配启用的标准省份");
            provinceId = found.getFirst().id();
            values.put("省份名称", found.getFirst().name());
        }
        for (var field : List.of("合同编号", "条款编号"))
            if (values.get(field).length() > 200) throw invalid(field + "不得超过200字");
        var clause = amount(values.get("条款金额"), "条款金额", true);
        var source = amount(values.get(SOURCE_AMOUNT), SOURCE_AMOUNT, false);
        values.put("条款金额", clause.toPlainString());
        values.put(SOURCE_AMOUNT, source.toPlainString());
        if (!values.get("审签日期").isBlank()) {
            try {
                values.put(
                        "审签日期", LocalDate.parse(values.get("审签日期").replace('/', '-')).toString());
            } catch (DateTimeParseException e) {
                throw invalid("审签日期须为有效的YYYY-MM-DD日期");
            }
        }
        return new Source(
                Collections.unmodifiableMap(values),
                month,
                provinceId,
                organization,
                values.get("合同编号"),
                values.get("条款编号"),
                clause,
                source);
    }

    public Revenue.Recognition recognition(Revenue.Recognition value, BigDecimal limit) {
        if (value == null || value.amount() == null) throw invalid("认定金额必填");
        var amount = amount(value.amount().toPlainString(), "认定金额", true);
        if (amount.compareTo(limit) > 0) throw invalid("认定金额不能超过条款金额");
        String name = Objects.toString(value.reportedProjectName(), "").strip();
        if (name.isEmpty() || name.length() > 300) throw invalid("通报项目名称须为1至300字");
        String status = Objects.toString(value.projectStatus(), "").strip();
        if (!status.isBlank() && !Set.of("实施中", "已交付").contains(status))
            throw invalid("项目状态须为实施中或已交付");
        if (value.integratedProvinceId() != null
                && organizations.findEnabledProvinces().stream()
                        .noneMatch(p -> p.id().equals(value.integratedProvinceId())))
            throw invalid("对应省分（集成）未启用");
        return new Revenue.Recognition(amount, name, status, value.integratedProvinceId());
    }

    public static String month(String value) {
        if (value.matches("\\d{6}")) value = value.substring(0, 4) + "-" + value.substring(4);
        try {
            var month = YearMonth.parse(value);
            if (month.getYear() < 1900 || month.getYear() > 9999) throw invalid("账期年份无效");
            return month.toString();
        } catch (DateTimeParseException e) {
            throw invalid("账期须为有效YYYYMM或YYYY-MM月份");
        }
    }

    private static String shortProvince(String value) {
        return value.replaceAll("(壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市)$", "");
    }

    public static BigDecimal amount(String raw, String field, boolean nonnegative) {
        try {
            var number = new BigDecimal(raw.replace(",", "")).setScale(2, RoundingMode.UNNECESSARY);
            if (nonnegative && number.signum() < 0
                    || number.abs().compareTo(new BigDecimal("10000000000000000")) >= 0)
                throw invalid(field + "金额超出范围");
            return number;
        } catch (NumberFormatException | ArithmeticException e) {
            throw invalid(field + "须为金额（元），最多两位小数");
        }
    }

    public static BusinessException invalid(String text) {
        return new BusinessException(RevenueError.invalid(text));
    }
}
