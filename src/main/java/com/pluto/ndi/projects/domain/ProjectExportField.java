package com.pluto.ndi.projects.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import com.pluto.ndi.shared.web.BusinessException;

/** 导出字段白名单及稳定代码；枚举顺序同时作为字段选择器的展示顺序。 */
public enum ProjectExportField {
    PROJECT_CODE("projectCode", "项目编号"), PROJECT_NAME("projectName", "项目名称"),
    CUSTOMER_NAME("customerName", "客户名称"), INDUSTRY_CODE("industryCode", "行业代码"),
    TOTAL_AMOUNT("totalAmount", "项目总金额"), CURRENT_STAGE_CODE("currentStageCode", "当前阶段"),
    NETWORK_AMOUNT("networkAmount", "网络化金额"), GEWU_AMOUNT("gewuAmount", "格物金额"),
    BIG_DATA_AMOUNT("bigDataAmount", "大数据金额"), WANGWU_AMOUNT("wangwuAmount", "万悟金额"),
    CREATED_AT("createdAt", "创建时间"), PROVINCE("province", "省分"), CITY("city", "地市"),
    CURRENT_STAGE_NAME("currentStageName", "项目阶段"), SIGNING_SUBJECT("signingSubjectCode", "签约主体"),
    PROVINCE_LEADER("provinceLeaderName", "省分负责人"), CITY_MANAGER("cityAccountManagerName", "地市客户经理"),
    SUPPORT_EXPERTS("productSupportExpertNames", "产品单位支撑专家"), OPPORTUNITY_DATE("opportunityDate", "项目日期"),
    CUSTOMER_INDUSTRY("customerIndustry", "客户所属行业"), CATEGORIES("categories", "三化类别"),
    UPDATED_AT("updatedAt", "最近更新时间"), RESPONSIBLE_PERSONS("responsiblePersons","项目负责人");

    public static final int MAX_FIELDS = 23;
    private static final List<ProjectExportField> DEFAULTS = List.of(PROJECT_CODE, PROJECT_NAME, CUSTOMER_NAME,
            INDUSTRY_CODE, TOTAL_AMOUNT, CURRENT_STAGE_CODE, NETWORK_AMOUNT, GEWU_AMOUNT, BIG_DATA_AMOUNT, WANGWU_AMOUNT, CREATED_AT);
    private final String code;
    private final String label;
    ProjectExportField(String code, String label) { this.code = code; this.label = label; }
    public String code() { return code; }
    public String label() { return label; }
    public boolean defaultSelected() { return DEFAULTS.contains(this); }
    public static List<String> defaultCodes() { return DEFAULTS.stream().map(ProjectExportField::code).toList(); }

    public static List<ProjectExportField> select(List<String> codes) {
        if (codes == null) return DEFAULTS;
        if (codes.isEmpty() || codes.size() > MAX_FIELDS || new HashSet<>(codes).size() != codes.size()) {
            throw new BusinessException(ProjectErrorCode.INVALID_EXPORT_FIELDS);
        }
        return codes.stream().map(code -> Arrays.stream(values()).filter(field -> field.code.equals(code))
                .findFirst().orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_EXPORT_FIELDS))).toList();
    }
}
