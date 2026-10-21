ALTER TABLE project_monthly_state_event
    ADD COLUMN network_amount DECIMAL(18,2) NULL,
    ADD COLUMN gewu_amount DECIMAL(18,2) NULL,
    ADD COLUMN big_data_amount DECIMAL(18,2) NULL,
    ADD COLUMN wangwu_amount DECIMAL(18,2) NULL;

ALTER TABLE project_monthly_snapshot
    ADD COLUMN network_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN gewu_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN big_data_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN wangwu_amount DECIMAL(18,2) NOT NULL DEFAULT 0;

UPDATE project_monthly_state_event event
LEFT JOIN (
    SELECT project_id,
           SUM(CASE WHEN category_code = 'NETWORK' THEN expected_amount ELSE 0 END) AS network_amount,
           SUM(CASE WHEN category_code = 'GEWU' THEN expected_amount ELSE 0 END) AS gewu_amount,
           SUM(CASE WHEN category_code = 'BIG_DATA' THEN expected_amount ELSE 0 END) AS big_data_amount,
           SUM(CASE WHEN category_code = 'WANGWU' THEN expected_amount ELSE 0 END) AS wangwu_amount
    FROM project_category_amount GROUP BY project_id
) amounts ON amounts.project_id = event.project_id
SET event.network_amount = COALESCE(amounts.network_amount, 0),
    event.gewu_amount = COALESCE(amounts.gewu_amount, 0),
    event.big_data_amount = COALESCE(amounts.big_data_amount, 0),
    event.wangwu_amount = COALESCE(amounts.wangwu_amount, 0)
WHERE event.baseline = TRUE;

UPDATE project_monthly_snapshot snapshot
LEFT JOIN (
    SELECT project_id,
           SUM(CASE WHEN category_code = 'NETWORK' THEN expected_amount ELSE 0 END) AS network_amount,
           SUM(CASE WHEN category_code = 'GEWU' THEN expected_amount ELSE 0 END) AS gewu_amount,
           SUM(CASE WHEN category_code = 'BIG_DATA' THEN expected_amount ELSE 0 END) AS big_data_amount,
           SUM(CASE WHEN category_code = 'WANGWU' THEN expected_amount ELSE 0 END) AS wangwu_amount
    FROM project_category_amount GROUP BY project_id
) amounts ON amounts.project_id = snapshot.project_id
SET snapshot.network_amount = COALESCE(amounts.network_amount, 0),
    snapshot.gewu_amount = COALESCE(amounts.gewu_amount, 0),
    snapshot.big_data_amount = COALESCE(amounts.big_data_amount, 0),
    snapshot.wangwu_amount = COALESCE(amounts.wangwu_amount, 0);
