CREATE TABLE project_monthly_snapshot_control (
    id TINYINT NOT NULL PRIMARY KEY,
    activation_month DATE NOT NULL,
    last_completed_month DATE NULL,
    CONSTRAINT chk_project_monthly_snapshot_control_id CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_monthly_state_event (
    id BINARY(16) NOT NULL PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    effective_month DATE NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_id BINARY(16) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    baseline BOOLEAN NOT NULL,
    province_id BINARY(16) NULL,
    city_id BINARY(16) NULL,
    industry_code VARCHAR(3) NULL,
    stage_code VARCHAR(40) NULL,
    total_amount DECIMAL(18,2) NULL,
    UNIQUE KEY uk_project_monthly_state_event_source (source_kind, source_id),
    KEY idx_project_monthly_state_event_project_month (project_id, effective_month, recorded_at),
    CONSTRAINT fk_project_monthly_state_event_project FOREIGN KEY (project_id) REFERENCES project_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_monthly_snapshot (
    month_start DATE NOT NULL,
    project_id BINARY(16) NOT NULL,
    province_id BINARY(16) NOT NULL,
    city_id BINARY(16) NOT NULL,
    city_name VARCHAR(100) NOT NULL,
    industry_code VARCHAR(3) NOT NULL,
    industry_name VARCHAR(200) NOT NULL,
    stage_code VARCHAR(40) NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    revision_no INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (month_start, project_id),
    KEY idx_project_monthly_snapshot_province (month_start, province_id, stage_code),
    KEY idx_project_monthly_snapshot_city (month_start, city_id, stage_code),
    KEY idx_project_monthly_snapshot_industry (month_start, industry_code),
    CONSTRAINT fk_project_monthly_snapshot_project FOREIGN KEY (project_id) REFERENCES project_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_monthly_snapshot_revision (
    id BINARY(16) NOT NULL PRIMARY KEY,
    month_start DATE NOT NULL,
    project_id BINARY(16) NOT NULL,
    revision_no INT NOT NULL,
    source_kind VARCHAR(32) NOT NULL,
    source_id BINARY(16) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    before_stage_code VARCHAR(40) NOT NULL,
    after_stage_code VARCHAR(40) NOT NULL,
    before_industry_code VARCHAR(3) NOT NULL,
    after_industry_code VARCHAR(3) NOT NULL,
    before_total_amount DECIMAL(18,2) NOT NULL,
    after_total_amount DECIMAL(18,2) NOT NULL,
    revised_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_project_monthly_snapshot_revision_no (month_start, project_id, revision_no),
    KEY idx_project_monthly_snapshot_revision_source (source_kind, source_id),
    CONSTRAINT fk_project_monthly_snapshot_revision_snapshot
        FOREIGN KEY (month_start, project_id) REFERENCES project_monthly_snapshot (month_start, project_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
