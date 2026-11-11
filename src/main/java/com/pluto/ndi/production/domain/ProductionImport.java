package com.pluto.ndi.production.domain;


import java.util.*;

/** 未完成任务是唯一可恢复状态，确认后不保存可续导批次。 */
public final class ProductionImport extends com.pluto.ndi.shared.imports.ImportModels {
    private ProductionImport() {}

    public static final List<String> REQUIRED =
            List.of("接口人", "排产月份", "省分", "地市", "项目名称", "客户名称", "三化类别");
    public static final List<String> AMOUNTS =
            List.of("工业专网排产（万元）", "格物平台排产（万元）", "工业大数据排产（万元）", "万悟平台排产（万元）");

    private static final List<String> PRODUCT_AMOUNTS = List.of(
            "5G工业专网排产（万元）", "格物工业互联网平台排产（万元）",
            "工业大数据平台排产（万元）", "万悟工业智能体平台排产（万元）");

    public static List<String> templateFields() {
        return fields().stream().map(ProductionImport::fieldLabel).toList();
    }

    public static String fieldLabel(String field) {
        int index = AMOUNTS.indexOf(field);
        return index < 0 ? field : PRODUCT_AMOUNTS.get(index);
    }

    public static List<String> fields() {
        var fields = new ArrayList<>(REQUIRED);
        fields.addAll(AMOUNTS);
        fields.add("项目状态");
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
                                                ? "四项至少填写一项，万元、非负、最多两位小数；金额须对应已选类别"
                                                : "三化类别".equals(f)
                                                        ? "5G工业专网、格物工业互联网平台、工业大数据平台、万悟工业智能体平台，多项以分号分隔"
                                                        : "排产月份".equals(f) ? "yyyy-MM" : ""))
                .toList();
    }
}
