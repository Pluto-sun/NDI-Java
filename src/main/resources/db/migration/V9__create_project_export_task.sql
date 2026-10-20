CREATE TABLE project_export_task (
    id BINARY(16) NOT NULL,
    requested_by BINARY(16) NOT NULL,
    scope_level VARCHAR(20) NOT NULL,
    scope_organization_id BINARY(16) NOT NULL,
    filter_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    row_count INT NULL,
    requested_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_message VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_project_export_task_status_requested (status, requested_at),
    KEY idx_project_export_task_actor_requested (requested_by, requested_at),
    CONSTRAINT fk_project_export_task_actor
        FOREIGN KEY (requested_by) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE project_project
    ADD KEY idx_project_project_province_created (province_id, created_at DESC, id DESC),
    ADD KEY idx_project_project_city_created (city_id, created_at DESC, id DESC);

CREATE TABLE project_export_download (
    id BINARY(16) NOT NULL,
    export_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_project_export_download_export (export_id, occurred_at),
    CONSTRAINT fk_project_export_download_export
        FOREIGN KEY (export_id) REFERENCES project_export_task (id),
    CONSTRAINT fk_project_export_download_actor
        FOREIGN KEY (actor_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
