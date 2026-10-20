package com.pluto.ndi.projects.domain;

import java.util.Optional;

/** 项目应用场景的稳定代码，供主档、导入和基础字典共同使用。 */
public enum ProjectApplicationScenario {
    FLEXIBLE_PRODUCTION("柔性产线快速换产"),
    PROCESS_OPTIMIZATION("制造工艺优化"),
    QUALITY_TRACEABILITY("质量精准追溯"),
    EQUIPMENT_OAM("设备监控运维"),
    SAFETY_CONTROL("安全一体化管控"),
    ENERGY_CONTROL("能源智能管控"),
    SMART_WAREHOUSING("仓储智能管理");

    private final String label;

    ProjectApplicationScenario(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<ProjectApplicationScenario> fromCode(String code) {
        try {
            return Optional.of(valueOf(code));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public static Optional<ProjectApplicationScenario> fromLabel(String label) {
        for (var scenario : values()) {
            if (scenario.label.equals(label)) return Optional.of(scenario);
        }
        return Optional.empty();
    }
}
