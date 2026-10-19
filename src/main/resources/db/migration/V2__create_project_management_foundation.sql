CREATE TABLE organization_province (
    id BINARY(16) NOT NULL,
    province_code VARCHAR(32) NOT NULL,
    province_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organization_province_code (province_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE organization_city (
    id BINARY(16) NOT NULL,
    province_id BINARY(16) NOT NULL,
    city_code VARCHAR(32) NOT NULL,
    city_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organization_city_code (city_code),
    KEY idx_organization_city_province (province_id),
    CONSTRAINT fk_organization_city_province
        FOREIGN KEY (province_id) REFERENCES organization_province (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE identity_user_account (
    id BINARY(16) NOT NULL,
    login_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    province_id BINARY(16) NULL,
    city_id BINARY(16) NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_user_account_login_name (login_name),
    KEY idx_identity_user_account_province (province_id),
    KEY idx_identity_user_account_city (city_id),
    CONSTRAINT fk_identity_user_account_province
        FOREIGN KEY (province_id) REFERENCES organization_province (id),
    CONSTRAINT fk_identity_user_account_city
        FOREIGN KEY (city_id) REFERENCES organization_city (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE identity_user_role (
    user_id BINARY(16) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_identity_user_role_user
        FOREIGN KEY (user_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_industry_reference (
    industry_code VARCHAR(3) NOT NULL,
    industry_name VARCHAR(200) NOT NULL,
    section_code CHAR(1) NOT NULL,
    display_order SMALLINT NOT NULL,
    standard_version VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    PRIMARY KEY (industry_code),
    UNIQUE KEY uk_project_industry_reference_name (industry_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_project (
    id BINARY(16) NOT NULL,
    project_code VARCHAR(40) NOT NULL,
    province_id BINARY(16) NOT NULL,
    city_id BINARY(16) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    industry_code VARCHAR(3) NOT NULL,
    customer_need TEXT NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    current_stage_code VARCHAR(40) NOT NULL,
    province_leader_user_id BINARY(16) NOT NULL,
    city_account_manager_name VARCHAR(100) NOT NULL,
    version_no BIGINT NOT NULL,
    created_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_project_code (project_code),
    KEY idx_project_project_province (province_id),
    KEY idx_project_project_city (city_id),
    KEY idx_project_project_stage (current_stage_code),
    KEY idx_project_project_industry (industry_code),
    CONSTRAINT chk_project_project_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT fk_project_project_province
        FOREIGN KEY (province_id) REFERENCES organization_province (id),
    CONSTRAINT fk_project_project_city
        FOREIGN KEY (city_id) REFERENCES organization_city (id),
    CONSTRAINT fk_project_project_industry
        FOREIGN KEY (industry_code) REFERENCES project_industry_reference (industry_code),
    CONSTRAINT fk_project_project_province_leader
        FOREIGN KEY (province_leader_user_id) REFERENCES identity_user_account (id),
    CONSTRAINT fk_project_project_created_by
        FOREIGN KEY (created_by) REFERENCES identity_user_account (id),
    CONSTRAINT fk_project_project_updated_by
        FOREIGN KEY (updated_by) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_category_amount (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    category_code VARCHAR(40) NOT NULL,
    expected_amount DECIMAL(18,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_category_amount_project_category (project_id, category_code),
    CONSTRAINT chk_project_category_amount_non_negative CHECK (expected_amount >= 0),
    CONSTRAINT fk_project_category_amount_project
        FOREIGN KEY (project_id) REFERENCES project_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_stage_milestone (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    stage_code VARCHAR(40) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    description_text VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_project_stage_milestone_project_occurred (project_id, occurred_at),
    CONSTRAINT fk_project_stage_milestone_project
        FOREIGN KEY (project_id) REFERENCES project_project (id),
    CONSTRAINT fk_project_stage_milestone_actor
        FOREIGN KEY (actor_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_progress (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    progress_content TEXT NOT NULL,
    next_action TEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_project_progress_project_occurred (project_id, occurred_at),
    CONSTRAINT fk_project_progress_project
        FOREIGN KEY (project_id) REFERENCES project_project (id),
    CONSTRAINT fk_project_progress_actor
        FOREIGN KEY (actor_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_change_event (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    payload JSON NOT NULL,
    actor_id BINARY(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_change_event_project_version (project_id, aggregate_version),
    KEY idx_project_change_event_project_occurred (project_id, occurred_at),
    CONSTRAINT fk_project_change_event_project
        FOREIGN KEY (project_id) REFERENCES project_project (id),
    CONSTRAINT fk_project_change_event_actor
        FOREIGN KEY (actor_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
