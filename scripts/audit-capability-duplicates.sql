-- 在迁移 V55 前运行，按能力类型和业务键列出全部历史重复记录。
WITH names AS (
    SELECT 'GEWU' AS kind, id, province_id,
           NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."项目名称"'))), '') AS name,
           NULL AS application FROM capability_gewu_deployment WHERE deleted_at IS NULL
    UNION ALL
    SELECT kind, id, province_id,
           NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details,
               CASE WHEN kind = 'WANGWU' THEN '$."具体名称"' ELSE '$."智能体名称"' END))), ''),
           NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."应用项目名称"'))), '')
    FROM intelligence_capability
), candidates AS (
    SELECT *, UNHEX(SHA2(CASE kind
        WHEN 'GEWU' THEN name
        WHEN 'WANGWU' THEN name
        WHEN 'AGENTS_LIVE' THEN CONCAT(HEX(province_id), name)
        WHEN 'AGENTS_DEVELOPMENT' THEN CONCAT(LENGTH(name), ':', name, application)
    END, 256)) AS business_key
    FROM names
), duplicates AS (
    SELECT kind, business_key FROM candidates WHERE business_key IS NOT NULL
    GROUP BY kind, business_key HAVING COUNT(*) > 1
)
SELECT c.kind, HEX(c.business_key) AS business_key, BIN_TO_UUID(c.id) AS record_id,
       p.province_name, c.name, c.application
FROM candidates c JOIN duplicates d USING (kind, business_key)
LEFT JOIN organization_province p ON p.id = c.province_id
ORDER BY c.kind, c.business_key, c.id;
