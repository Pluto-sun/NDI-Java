package com.pluto.ndi.revenue.domain;

import java.util.*;

/** 未完成任务是唯一可恢复状态，确认后不保存可续导批次。 */
public final class RevenueImport extends com.pluto.ndi.shared.imports.ImportModels {
    private RevenueImport() {}

    private static final Set<String> CONDITIONAL = Set.of("合同编号", "地市/组织名称");

    public static final List<String> REQUIRED =
            com.pluto.ndi.revenue.util.RevenueFields.FIELDS.stream()
                    .filter(com.pluto.ndi.revenue.util.RevenueFields.REQUIRED::contains)
                    .filter(field -> !CONDITIONAL.contains(field))
                    .toList();

    public static final String AMOUNT = "认定金额";
    public static final String PROJECT = "通报项目名称";
    public static final String STATUS = "项目状态";
    public static final String PROVINCE = "对应省分（集成）";

    public static List<String> fields() {
        var fields = new ArrayList<>(com.pluto.ndi.revenue.util.RevenueFields.FIELDS);
        fields.addAll(List.of(AMOUNT, PROJECT, STATUS, PROVINCE));
        return List.copyOf(fields);
    }

    public static List<Rule> rules() {
        return fields().stream()
                .map(
                        field ->
                                new Rule(
                                        field,
                                        REQUIRED.contains(field) ? "REQUIRED" : CONDITIONAL.contains(field) ? "CONDITIONAL" : "OPTIONAL",
                                        switch (field) {
                                            case "省份名称" -> "必填；标准省份、具体组织或数科；旧模板的 -- 按具体组织处理";
                                            case "合同编号" -> "网络化可留空，数字化、智能化必填";
                                            case "条款编号" -> "选填；留空或--统一为空，纯数字整数去除前导零（002→2）";
                                            case "地市/组织名称" -> "标准省份的网络化可留空；其他分类必填。具体组织须填全称，数科自动补齐";
                                            case "审签日期" -> "选填，支持横线或斜线日期及日期时间；--表示无日期";
                                            case "月账期", "费用账期" -> "YYYYMM或YYYY-MM";
                                            case "条款金额", "产品本月计收不含税额/标品含税收入" -> "元，允许负数和零，四舍五入到两位小数";
                                            case STATUS -> "选填；实施中、已交付或已上线，随认定结果导入";
                                            case AMOUNT -> "元，允许负数，四舍五入到两位小数；网络化舍入后须非零";
                                            case PROVINCE -> "直接认定时必填，支持启用的省份、数科及数智";
                                            case PROJECT -> "网络化选填；数字化、智能化直接认定时必填";
                                            default -> "";
                                        }))
                .toList();
    }
}
