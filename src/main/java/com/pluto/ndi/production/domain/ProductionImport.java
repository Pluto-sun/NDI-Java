package com.pluto.ndi.production.domain;


import java.util.*;

/** 未完成任务是唯一可恢复状态，确认后不保存可续导批次。 */
public final class ProductionImport extends com.pluto.ndi.shared.imports.ImportModels {
    private ProductionImport() {}

    public static final List<String> REQUIRED =
            List.of("接口人", "排产月份", "省分", "地市", "项目名称");
    // 保留已有订单金额的映射键，使升级前暂存的导入任务可继续。
    public static final List<String> AMOUNTS = List.of(
            "工业专网排产（万元）",
            "格物平台排产（万元）", "格物工业互联网平台-数智业务管理系统预计计收金额",
            "工业大数据排产（万元）", "工业大数据平台-数智业务管理系统预计计收金额",
            "万悟平台排产（万元）", "万悟工业智能体平台-数智业务管理系统预计计收金额");

    private static final List<String> PRODUCT_AMOUNTS = List.of(
            "工业专网产品收入排产",
            "格物工业互联网平台-政企运营平台预计下单金额",
            "格物工业互联网平台-数智业务管理系统预计计收金额",
            "工业大数据平台-政企运营平台预计下单金额",
            "工业大数据平台-数智业务管理系统预计计收金额",
            "万悟工业智能体平台-政企运营平台预计下单金额",
            "万悟工业智能体平台-数智业务管理系统预计计收金额");

    public static List<String> templateFields() {
        return fields().stream().map(ProductionImport::fieldLabel).toList();
    }

    public static String fieldLabel(String field) {
        int index = AMOUNTS.indexOf(field);
        return index < 0 ? field : PRODUCT_AMOUNTS.get(index);
    }

    public static List<String> fields() {
        var fields = new ArrayList<>(REQUIRED);
        fields.add("客户名称");
        fields.addAll(AMOUNTS);
        fields.add("项目状态");
        fields.add("产品简介");
        fields.add("备注");
        return List.copyOf(fields);
    }

    public static List<Rule> rules() {
        return fields().stream()
                .map(
                        f ->
                                new Rule(
                                        f,
                                        REQUIRED.contains(f)
                                                ? "REQUIRED"
                                                : AMOUNTS.contains(f) ? "CONDITIONAL" : "OPTIONAL",
                                        AMOUNTS.contains(f)
                                                ? "至少一项金额大于 0，单位万元，非负且最多四位小数"
                                                : "排产月份".equals(f) ? "yyyy-MM" : ""))
                .toList();
    }
}
