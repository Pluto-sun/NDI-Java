ALTER TABLE project_import_row
    ADD COLUMN source_payload JSON NULL,
    ADD COLUMN revision_no INT NOT NULL DEFAULT 0;

CREATE INDEX idx_project_import_batch_recovery
    ON project_import_batch (status, confirmed_at);

CREATE TABLE project_import_row_revision (
    id BINARY(16) NOT NULL PRIMARY KEY,
    row_id BINARY(16) NOT NULL,
    revision_no INT NOT NULL,
    actor_id BINARY(16) NOT NULL,
    before_payload JSON NOT NULL,
    after_payload JSON NOT NULL,
    result_status VARCHAR(24) NOT NULL,
    errors JSON NOT NULL,
    revised_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_project_import_row_revision (row_id, revision_no),
    CONSTRAINT fk_project_import_revision_row FOREIGN KEY (row_id) REFERENCES project_import_row(id),
    CONSTRAINT fk_project_import_revision_actor FOREIGN KEY (actor_id) REFERENCES identity_user_account(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
