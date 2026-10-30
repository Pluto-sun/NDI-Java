package com.pluto.ndi.revenue.domain;

import java.util.*;

/** 未完成任务是唯一可恢复状态，确认后不保存可续导批次。 */
public final class RevenueImport extends com.pluto.ndi.shared.imports.ImportModels {
    private RevenueImport() {}

    public static final List<String> REQUIRED =
            com.pluto.ndi.revenue.util.RevenueFields.FIELDS.stream()
                    .filter(com.pluto.ndi.revenue.util.RevenueFields.REQUIRED::contains)
                    .toList();

    public static List<String> fields() {
        return com.pluto.ndi.revenue.util.RevenueFields.FIELDS;
    }

    public static List<Rule> rules() {
        return fields().stream()
                .map(
                        field ->
                                new Rule(
                                        field,
                                        REQUIRED.contains(field) ? "REQUIRED" : "OPTIONAL",
                                        field.equals("省份名称")
                                                ? "标准省份或具体组织；--按具体组织处理"
                                                : field.equals("地市/组织名称")
                                                        ? "文本，具体组织必填，不限制为行政地市"
                                                        : field.equals("月账期")
                                                                        || field.equals("费用账期")
                                                                ? "YYYYMM或YYYY-MM"
                                                                : field.equals("条款金额")
                                                                                || field.equals(
                                                                                        com.pluto
                                                                                                .ndi
                                                                                                .revenue
                                                                                                .util
                                                                                                .RevenueFields
                                                                                                .SOURCE_AMOUNT)
                                                                        ? "元，最多两位小数，不进行税额换算"
                                                                        : ""))
                .toList();
    }
}
