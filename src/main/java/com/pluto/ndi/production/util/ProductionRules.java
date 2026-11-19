package com.pluto.ndi.production.util;

import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.shared.web.BusinessException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.*;

/** 排产规则独立于项目生命周期；导入和手工维护共用。 */
public final class ProductionRules {
    private ProductionRules() {}

    public static String normalize(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Z}]+", "");
    }

    public static ProductionPlan.Command validate(ProductionPlan.Command c) {
        if (c == null) fail("请填写排产信息");
        var reviewer = text(c.reviewerName(), 100, "接口人");
        var project = text(c.projectName(), 300, "项目名称");
        var customer = Objects.toString(c.customerName(), "").strip();
        if (customer.length() > 300) fail("客户名称最多 300 字");
        try {
            if (c.planMonth() == null
                    || !c.planMonth().matches("[0-9]{4}-[0-9]{2}")
                    || YearMonth.parse(c.planMonth()).getYear() < 1900
                    || YearMonth.parse(c.planMonth()).getYear() > 9999) fail("排产月份格式应为 yyyy-MM");
        } catch (java.time.DateTimeException e) {
            fail("排产月份格式应为 yyyy-MM");
        }
        if (c.provinceId() == null || c.cityId() == null) fail("省份与地市必填");
        var categories = new ArrayList<String>();
        addCategory(categories, "NETWORK", c.networkOrderAmount());
        addCategory(categories, "GEWU", c.gewuOrderAmount(), c.gewuRevenueAmount());
        addCategory(categories, "BIG_DATA", c.bigDataOrderAmount(), c.bigDataRevenueAmount());
        addCategory(categories, "WANGWU", c.wangwuOrderAmount(), c.wangwuRevenueAmount());
        if (categories.isEmpty()) fail("至少填写一项大于 0 的排产金额");
        String status = c.projectStatus() == null ? null : c.projectStatus().strip();
        if (status != null && status.length() > 200) fail("项目状态最多 200 字");
        String remark = c.remark() == null ? null : c.remark().strip();
        if (remark != null && remark.length() > 2000) fail("备注最多 2000 字");
        var introduction = c.productIntroduction() == null ? "" : c.productIntroduction().strip();
        if (introduction.length() > 2000) fail("产品简介最多 2000 字");
        return new ProductionPlan.Command(
                reviewer,
                c.planMonth(),
                c.provinceId(),
                c.cityId(),
                project,
                customer,
                List.copyOf(categories),
                c.networkOrderAmount(),
                c.gewuOrderAmount(),
                c.gewuRevenueAmount(),
                c.bigDataOrderAmount(),
                c.bigDataRevenueAmount(),
                c.wangwuOrderAmount(),
                c.wangwuRevenueAmount(),
                status == null || status.isEmpty() ? null : status,
                remark == null || remark.isEmpty() ? null : remark,
                introduction.isEmpty() ? null : introduction);
    }

    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000000000000");

    private static void addCategory(List<String> categories, String code, BigDecimal... amounts) {
        boolean positive = false;
        for (var amount : amounts) {
            if (amount == null) continue;
            if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 4
                    || amount.compareTo(AMOUNT_LIMIT) >= 0)
                fail("排产金额须为非负数，最多四位小数，整数部分最多十六位");
            positive |= amount.signum() > 0;
        }
        if (positive) categories.add(code);
    }

    private static String text(String value, int limit, String label) {
        if (value == null || value.isBlank() || value.strip().length() > limit)
            fail(label + "必填且最多 " + limit + " 字");
        return value.strip();
    }

    private static void fail(String message) {
        throw new BusinessException(ProductionError.invalid(message));
    }
}
