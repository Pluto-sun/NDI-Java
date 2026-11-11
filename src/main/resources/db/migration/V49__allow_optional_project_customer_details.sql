ALTER TABLE project_project MODIFY industry_code VARCHAR(3) NULL;
ALTER TABLE project_monthly_snapshot MODIFY industry_code VARCHAR(3) NULL;
ALTER TABLE project_monthly_snapshot_revision
    MODIFY before_industry_code VARCHAR(3) NULL,
    MODIFY after_industry_code VARCHAR(3) NULL;
