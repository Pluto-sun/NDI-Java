package com.pluto.ndi.capabilities;

import java.util.*;

public enum IntelligenceKind {
    WANGWU("接口人,省分,部署地点,具体名称,部署进度,部署版本,版本更新日期", Set.of("接口人", "省分", "部署地点", "具体名称", "部署进度")),
    AGENTS_LIVE(
            "接口人,省分,省分智能化接口人,环节,智能体名称,应用项目名称,最终客户名称,智能体功能简述,智能体包含组件情况,是否为三方生态迁移,是否计收,是否在计收表中出现,上线月份,计收月份",
            Set.of(
                    "接口人",
                    "省分",
                    "省分智能化接口人",
                    "环节",
                    "智能体名称",
                    "应用项目名称",
                    "最终客户名称",
                    "智能体功能简述",
                    "是否为三方生态迁移",
                    "是否计收",
                    "是否在计收表中出现")),
    AGENTS_DEVELOPMENT(
            "接口人,省分,省分智能化接口人,环节,智能体名称,应用项目名称,最终客户名称,智能体功能简述,智能体包含组件情况,状态,是否为三方生态迁移,开发进度,智能体开发开始时间,是否计收,计收月份",
            Set.of("省分"));

    final List<String> fields;
    final Set<String> required;

    IntelligenceKind(String fields, Set<String> required) {
        this.fields = List.of(fields.split(","));
        this.required = required;
    }

    String module() {
        return name().toLowerCase(Locale.ROOT);
    }

    static final Map<String, List<String>> OPTIONS =
            Map.of(
                    "部署进度",
                    List.of("已部署", "未部署"),
                    "是否为三方生态迁移",
                    List.of("是", "否"),
                    "是否在计收表中出现",
                    List.of("是", "否"),
                    "是否计收",
                    List.of("已计收", "未计收"),
                    "环节",
                    List.of("运营管理", "研发设计", "营销服务", "生产制造"),
                    "状态",
                    List.of("开发中", "已完成", "待开发"));
    static final Set<String> MONTHS = Set.of("上线月份", "计收月份", "智能体开发开始时间");
}
