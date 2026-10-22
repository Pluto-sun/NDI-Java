-- 项目日期独立于普通资料更新时间、追加进展时间和阶段发生月份。
ALTER TABLE project_project ADD COLUMN opportunity_date DATETIME(6) NULL;

UPDATE project_project project
LEFT JOIN (
    SELECT project_id, MAX(occurred_at) AS approved_at
    FROM project_change_event
    WHERE event_type IN ('STAGE_ADVANCEMENT_APPROVED', 'MAJOR_CHANGE_APPROVED')
    GROUP BY project_id
) approved ON approved.project_id = project.id
LEFT JOIN (
    SELECT project_id, MIN(occurred_at) AS created_at
    FROM project_change_event
    WHERE event_type = 'PROJECT_CREATED'
      AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.source')), '') <> 'LEGACY_MYSQL'
    GROUP BY project_id
) creation ON creation.project_id = project.id
LEFT JOIN (
    SELECT project_id FROM project_source_record WHERE source_system = 'LEGACY_MYSQL'
    UNION
    SELECT project_id FROM project_change_event
    WHERE event_type = 'PROJECT_CREATED' AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.source')) = 'LEGACY_MYSQL'
) legacy ON legacy.project_id = project.id
SET project.opportunity_date = COALESCE(approved.approved_at,
    CASE WHEN legacy.project_id IS NULL THEN COALESCE(creation.created_at, project.created_at) ELSE NULL END);

-- 已丢失旧日期的历史记录保留空值；后续从旧库原字段恢复，禁止用迁移时刻替代。
CREATE INDEX idx_project_opportunity_date ON project_project(opportunity_date, id);
CREATE INDEX idx_project_city_opportunity_date ON project_project(city_id, opportunity_date, id);
CREATE INDEX idx_project_province_opportunity_date ON project_project(province_id, opportunity_date, id);
