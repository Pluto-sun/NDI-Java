package com.pluto.ndi.projects.util;

import java.text.Normalizer;
import java.util.*;

/** 区分列映射完整性与行内值校验，条件必填只针对当前文件实际涉及的业务。 */
public final class ProjectImportMappingRules {
    public static final Set<String> REQUIRED =
            Set.of("省分", "地市", "客户名称", "客户所属行业", "项目名称", "项目建设内容", "三化类别", "项目阶段", "项目总金额（万）");
    public static final List<String> PEOPLE = List.of("项目负责人", "省分负责人", "地市客户经理", "产品单位支撑专家");

    private ProjectImportMappingRules() {}

    public static List<String> missing(
            Map<String, String> mapping, List<ProjectExcelReader.ExcelRow> rows) {
        var missing = new TreeSet<String>();
        REQUIRED.stream().filter(field -> !mapped(mapping, field)).forEach(missing::add);
        if (PEOPLE.stream().noneMatch(field -> mapped(mapping, field))) missing.add("项目负责人（或旧人员列）");
        for (var row : rows) {
            for (var label : row.get("三化类别").split("[,，、]")) {
                String name = Normalizer.normalize(label.strip(), Normalizer.Form.NFKC);
                String field =
                        switch (name) {
                            case "网络化" -> "网络化预计金额（万）";
                            case "格物", "数字化(格物)" -> "格物预计金额（万）";
                            case "大数据", "数字化(大数据)" -> "大数据预计金额（万）";
                            case "万悟", "智能化", "智能化(万悟)" -> "万悟预计金额（万）";
                            default -> null;
                        };
                if (field != null && !mapped(mapping, field)) missing.add(field);
            }
            String stage = row.get("项目阶段");
            if ("计收".equals(stage)
                    || Set.of("签约", "已终止").contains(stage)
                            && (!row.get("合同编号").isBlank() || !row.get("签约金额（万）").isBlank()))
                for (var field : List.of("合同编号", "签约金额（万）"))
                    if (!mapped(mapping, field)) missing.add(field);
        }
        return List.copyOf(missing);
    }

    private static boolean mapped(Map<String, String> mapping, String field) {
        return mapping.get(field) != null && !mapping.get(field).isBlank();
    }
}
