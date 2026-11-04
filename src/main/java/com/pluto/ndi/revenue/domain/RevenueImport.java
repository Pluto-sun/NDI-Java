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
                                        switch (field) {
                                            case "省份名称" -> "选填；标准省份、具体组织或数科；留空为未归属，--按具体组织处理";
                                            case "条款编号" -> "选填；可留空或填--，纯数字整数去除前导零（002→2）";
                                            case "地市/组织名称" -> "具体组织必填；数科仅允许联通数字科技有限公司本部";
                                            case "审签日期" -> "选填，支持横线或斜线日期及日期时间；--表示无日期";
                                            case "月账期", "费用账期" -> "YYYYMM或YYYY-MM";
                                            case "条款金额", "产品本月计收不含税额/标品含税收入" -> "元，最多两位小数，不进行税额换算";
                                            default -> "";
                                        }))
                .toList();
    }
}
