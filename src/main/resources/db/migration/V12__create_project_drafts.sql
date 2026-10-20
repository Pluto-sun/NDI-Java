CREATE TABLE project_draft (
    id BINARY(16) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_project_draft_owner_updated (owner_id, updated_at),
    CONSTRAINT fk_project_draft_owner FOREIGN KEY (owner_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
