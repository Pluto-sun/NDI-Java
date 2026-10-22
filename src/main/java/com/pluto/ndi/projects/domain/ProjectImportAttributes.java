package com.pluto.ndi.projects.domain;

/** 仅保留旧模板的属性原值，不定义重要度枚举，也不授予人员权限。 */
public record ProjectImportAttributes(String importanceCode, String supportIndustryExpert) {
    public ProjectImportAttributes {
        importanceCode = importanceCode == null || importanceCode.isBlank() ? "NORMAL" : importanceCode.strip();
        supportIndustryExpert = supportIndustryExpert == null || supportIndustryExpert.isBlank()
                ? null : supportIndustryExpert.strip();
        if (importanceCode.length() > 30) throw new IllegalArgumentException("重要度不得超过30个字符");
        if (supportIndustryExpert != null && supportIndustryExpert.length() > 500) {
            throw new IllegalArgumentException("支撑行业专家不得超过500个字符");
        }
    }
}
