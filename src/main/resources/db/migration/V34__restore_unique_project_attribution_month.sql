-- 所属月份记录当前唯一归属，与每个月保留一份的月末快照分开。
ALTER TABLE project_project ADD COLUMN attribution_month DATE NULL;

-- 仅重建 Java 库已有事实，不连接旧系统数据库；阶段发生时刻按经营时区归月。
UPDATE project_project project
LEFT JOIN (
    SELECT project_id, effective_at FROM (
        SELECT project_id,
               CASE WHEN event_type = 'STAGE_ADVANCEMENT_APPROVED'
                    THEN COALESCE(STR_TO_DATE(LEFT(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.stageOccurredAt')), 19),
                                              '%Y-%m-%dT%H:%i:%s'), occurred_at)
                    ELSE occurred_at END effective_at,
               ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY occurred_at DESC, aggregate_version DESC) position_no
        FROM project_change_event
        WHERE event_type = 'STAGE_ADVANCEMENT_APPROVED'
           OR (event_type = 'MAJOR_CHANGE_APPROVED'
               AND JSON_UNQUOTE(JSON_EXTRACT(payload, '$.before.stageCode'))
                   <> JSON_UNQUOTE(JSON_EXTRACT(payload, '$.after.stageCode')))
    ) changes WHERE position_no = 1
) approved ON approved.project_id = project.id
SET project.attribution_month = CAST(DATE_FORMAT(
    DATE_ADD(COALESCE(approved.effective_at,
        (SELECT MIN(source.imported_at) FROM project_source_record source
         WHERE source.project_id = project.id AND source.source_system = 'EXCEL_IMPORT'),
        project.created_at), INTERVAL 8 HOUR), '%Y-%m-01') AS DATE);

CREATE INDEX idx_project_attribution_month ON project_project(attribution_month, current_stage_code);
CREATE INDEX idx_project_province_attribution_month ON project_project(province_id, attribution_month, city_id);
ALTER TABLE project_project ADD CONSTRAINT ck_project_attribution_month
    CHECK (attribution_month IS NULL OR DAYOFMONTH(attribution_month) = 1);
