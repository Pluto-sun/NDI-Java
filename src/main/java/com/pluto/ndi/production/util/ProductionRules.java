package com.pluto.ndi.production.util;

import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.shared.web.BusinessException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.*;

/** 排产规则独立于项目生命周期；导入和手工维护共用。 */
public final class ProductionRules {
    public static final List<String> CODES = List.of("NETWORK", "GEWU", "BIG_DATA", "WANGWU");

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
        var customer = text(c.customerName(), 300, "客户名称");
        try {
            if (c.planMonth() == null
                    || !c.planMonth().matches("[0-9]{4}-[0-9]{2}")
                    || YearMonth.parse(c.planMonth()).getYear() < 1900
                    || YearMonth.parse(c.planMonth()).getYear() > 9999) fail("排产月份格式应为 yyyy-MM");
        } catch (java.time.DateTimeException e) {
            fail("排产月份格式应为 yyyy-MM");
        }
        if (c.provinceId() == null || c.cityId() == null) fail("省份与地市必填");
        if (c.categories() == null
                || c.categories().isEmpty()
                || !CODES.containsAll(c.categories())
                || new HashSet<>(c.categories()).size() != c.categories().size())
            fail("至少选择一个有效三化类别");
        BigDecimal[] amounts = {
            c.networkAmount(), c.gewuAmount(), c.bigDataAmount(), c.wangwuAmount()
        };
        boolean filled = false;
        for (int i = 0; i < amounts.length; i++) {
            var amount = amounts[i];
            if (amount == null) continue;
            filled = true;
            if (amount.signum() < 0
                    || amount.stripTrailingZeros().scale() > 2
                    || amount.compareTo(new BigDecimal("10000000000000000")) >= 0)
                fail("排产金额须为非负数，最多两位小数");
            if (!c.categories().contains(CODES.get(i))) fail("填写排产金额前须勾选对应三化类别");
        }
        if (!filled) fail("四项排产金额至少填写一项，允许填写 0");
        String status = c.projectStatus() == null ? null : c.projectStatus().strip();
        if (status != null && status.length() > 200) fail("项目状态最多 200 字");
        return new ProductionPlan.Command(
                reviewer,
                c.planMonth(),
                c.provinceId(),
                c.cityId(),
                project,
                customer,
                List.copyOf(c.categories()),
                amounts[0],
                amounts[1],
                amounts[2],
                amounts[3],
                status == null || status.isEmpty() ? null : status);
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
