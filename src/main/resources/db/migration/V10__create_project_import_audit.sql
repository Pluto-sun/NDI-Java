CREATE TABLE project_import_batch (
    id BINARY(16) NOT NULL PRIMARY KEY,
    actor_id BINARY(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_rows INT NOT NULL,
    valid_rows INT NOT NULL,
    failed_rows INT NOT NULL,
    imported_rows INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    CONSTRAINT fk_project_import_batch_actor FOREIGN KEY (actor_id) REFERENCES identity_user_account(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_import_row (
    id BINARY(16) NOT NULL PRIMARY KEY,
    batch_id BINARY(16) NOT NULL,
    source_row_number INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    normalized_key VARCHAR(512) NULL,
    row_payload JSON NOT NULL,
    errors JSON NOT NULL,
    project_id BINARY(16) NULL,
    UNIQUE KEY uk_project_import_row_number (batch_id, source_row_number),
    KEY idx_project_import_row_status (batch_id, status),
    CONSTRAINT fk_project_import_row_batch FOREIGN KEY (batch_id) REFERENCES project_import_batch(id),
    CONSTRAINT fk_project_import_row_project FOREIGN KEY (project_id) REFERENCES project_project(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_source_record (
    id BINARY(16) NOT NULL PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    source_system VARCHAR(40) NOT NULL,
    source_record_id VARCHAR(100) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    imported_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_project_source_record_source (source_system, source_record_id),
    KEY idx_project_source_record_project (project_id),
    CONSTRAINT fk_project_source_record_project FOREIGN KEY (project_id) REFERENCES project_project(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE project_progress
    ADD COLUMN owner_name VARCHAR(100) NULL,
    ADD COLUMN due_date DATE NULL;

CREATE TABLE project_legacy_member_snapshot (
    id BINARY(16) NOT NULL PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    source_member_id VARCHAR(36) NOT NULL,
    role_code VARCHAR(40) NOT NULL,
    person_name VARCHAR(100) NOT NULL,
    source_person_id VARCHAR(36) NULL,
    UNIQUE KEY uk_legacy_member_source (source_member_id),
    CONSTRAINT fk_legacy_member_project FOREIGN KEY (project_id) REFERENCES project_project(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_legacy_record_map (
    source_table VARCHAR(40) NOT NULL,
    source_record_id VARCHAR(36) NOT NULL,
    project_id BINARY(16) NOT NULL,
    target_record_id BINARY(16) NOT NULL,
    PRIMARY KEY (source_table, source_record_id),
    KEY idx_legacy_record_map_project (project_id),
    CONSTRAINT fk_legacy_record_map_project FOREIGN KEY (project_id) REFERENCES project_project(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
