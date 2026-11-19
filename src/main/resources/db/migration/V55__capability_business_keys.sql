-- 先检查两张表，历史重复会在任何业务表结构变更之前阻止迁移。
CREATE TEMPORARY TABLE capability_business_key_preflight (
    kind VARCHAR(32) NOT NULL,
    business_key BINARY(32) NOT NULL,
    PRIMARY KEY (kind, business_key)
);

INSERT INTO capability_business_key_preflight(kind, business_key)
SELECT kind, business_key FROM (
SELECT 'GEWU' AS kind, UNHEX(SHA2(CASE WHEN deleted_at IS NULL THEN NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."项目名称"'))), '') END, 256)) AS business_key, id FROM capability_gewu_deployment
UNION ALL
SELECT kind, UNHEX(SHA2(CASE kind
        WHEN 'WANGWU' THEN NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."具体名称"'))), '')
        WHEN 'AGENTS_LIVE' THEN CONCAT(HEX(province_id), LENGTH(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), '')), ':', NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), ''), NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."应用项目名称"'))), ''))
        WHEN 'AGENTS_DEVELOPMENT' THEN CONCAT(LENGTH(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), '')), ':', NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), ''), NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."应用项目名称"'))), ''))
    END, 256)), id FROM intelligence_capability
) AS candidates WHERE business_key IS NOT NULL;

DROP TEMPORARY TABLE capability_business_key_preflight;

ALTER TABLE capability_gewu_deployment
    ADD COLUMN business_key BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(CASE WHEN deleted_at IS NULL THEN NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."项目名称"'))), '') END, 256))) STORED,
    ADD CONSTRAINT uq_gewu_deployment_business_key UNIQUE (business_key);

ALTER TABLE intelligence_capability
    ADD COLUMN business_key BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(CASE kind
        WHEN 'WANGWU' THEN NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."具体名称"'))), '')
        WHEN 'AGENTS_LIVE' THEN CONCAT(HEX(province_id), LENGTH(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), '')), ':', NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), ''), NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."应用项目名称"'))), ''))
        WHEN 'AGENTS_DEVELOPMENT' THEN CONCAT(LENGTH(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), '')), ':', NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."智能体名称"'))), ''), NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(details, '$."应用项目名称"'))), ''))
    END, 256))) STORED,
    ADD CONSTRAINT uq_intelligence_capability_business_key UNIQUE (kind, business_key);
