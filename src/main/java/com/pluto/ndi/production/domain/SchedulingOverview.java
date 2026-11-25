package com.pluto.ndi.production.domain;

import java.util.List;

/** 金额为万元，兑现率为百分数，使用十进制文本保留精度。 */
public record SchedulingOverview(
        long plannedProjects, long recognizedProjects, Amounts total,
        String productionScope, String revenueScope, boolean empty,
        List<Category> categories, List<Month> months, List<Province> provinces) {

    public record Amounts(String planned, String recognized, String gap, String rate) {}
    public record Category(String name, Amounts amounts) {}
    public record Month(String month, Amounts amounts, String plannedIncrease, String recognizedIncrease) {}
    public record Province(String key, String name, String adcode, boolean province,
                           Amounts total, List<Amounts> months) {}
}
