package com.pluto.ndi.revenue.util;

import java.util.Map;

/** 来源组织选项由创建、导入、模板共用，不作为账号权限来源。 */
public final class RevenueSourceOptions {
    public static final String DIGITAL_TECH = "数科";
    public static final Map<String, String> FIXED_ORGANIZATIONS =
            Map.of(DIGITAL_TECH, "联通数字科技有限公司本部");

    private RevenueSourceOptions() {}
}
