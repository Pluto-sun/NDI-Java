package com.pluto.ndi.projects.mapper;

/** 当前主档金额先按项目聚合，避免分类表连接导致项目数量与总金额重复。 */
public final class ProjectCurrentMetricSql {
    private ProjectCurrentMetricSql() { }

    public static final String FROM = """
            FROM project_project project
            LEFT JOIN (
                SELECT project_id,
                       SUM(CASE WHEN category_code = 'NETWORK' THEN expected_amount ELSE 0 END) network_amount,
                       SUM(CASE WHEN category_code = 'GEWU' THEN expected_amount ELSE 0 END) gewu_amount,
                       SUM(CASE WHEN category_code = 'BIG_DATA' THEN expected_amount ELSE 0 END) big_data_amount,
                       SUM(CASE WHEN category_code = 'WANGWU' THEN expected_amount ELSE 0 END) wangwu_amount
                FROM project_category_amount GROUP BY project_id
            ) category ON category.project_id = project.id
            WHERE 1 = 1
            """;

    /** 升级后的在线记录均有归属；兼容未经过在线创建入口的隔离测试数据。 */
    public static final String ATTRIBUTION_MONTH = "COALESCE(project.attribution_month, "
            + "CAST(DATE_FORMAT(DATE_ADD(project.created_at, INTERVAL 8 HOUR), '%Y-%m-01') AS DATE))";
}
