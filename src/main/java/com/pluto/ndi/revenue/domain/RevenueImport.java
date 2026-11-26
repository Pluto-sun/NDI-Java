package com.pluto.ndi.revenue.domain;

import java.util.*;

/** 未完成任务是唯一可恢复状态，确认后不保存可续导批次。 */
public final class RevenueImport extends com.pluto.ndi.shared.imports.ImportModels {
    private RevenueImport() {}

    public static final List<String> REQUIRED =
            com.pluto.ndi.revenue.util.RevenueFields.FIELDS.stream()
                    .filter(com.pluto.ndi.revenue.util.RevenueFields.REQUIRED::contains)
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
                                        REQUIRED.contains(field) ? "REQUIRED" : "OPTIONAL",
                                        switch (field) {
                                            case "省份名称" -> "必填；标准省份、具体组织或数科；旧模板的 -- 按具体组织处理";
                                            case "条款编号" -> "选填；留空或--统一为空，纯数字整数去除前导零（002→2）";
                                            case "地市/组织名称" -> "必填；数科自动使用联通数字科技有限公司本部";
                                            case "审签日期" -> "选填，支持横线或斜线日期及日期时间；--表示无日期";
                                            case "月账期", "费用账期" -> "YYYYMM或YYYY-MM";
                                            case "条款金额", "产品本月计收不含税额/标品含税收入" -> "元，最多两位小数，不进行税额换算";
                                            case STATUS -> "选填；实施中、已交付或已上线，随认定结果导入";
                                            case AMOUNT, PROVINCE -> "网络化必填；认定金额须大于零";
                                            case PROJECT -> "网络化选填；数字化、智能化直接认定时必填";
                                            default -> "";
                                        }))
                .toList();
    }
}
