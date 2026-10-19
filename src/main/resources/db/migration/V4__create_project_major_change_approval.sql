ALTER TABLE project_project
    ADD COLUMN contract_no VARCHAR(100) NULL,
    ADD COLUMN signed_amount DECIMAL(18,2) NULL,
    ADD UNIQUE KEY uk_project_project_contract_no (contract_no),
    ADD CONSTRAINT chk_project_project_signed_amount_non_negative CHECK (signed_amount IS NULL OR signed_amount >= 0);

CREATE TABLE project_approval_request (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    request_type VARCHAR(40) NOT NULL,
    change_type VARCHAR(40) NULL,
    status VARCHAR(40) NOT NULL,
    initiator_level VARCHAR(20) NOT NULL,
    initiator_id BINARY(16) NOT NULL,
    approver_id BINARY(16) NOT NULL,
    base_version BIGINT NOT NULL,
    from_stage_code VARCHAR(40) NOT NULL,
    original_project_name VARCHAR(200) NOT NULL,
    original_total_amount DECIMAL(18,2) NOT NULL,
    original_contract_no VARCHAR(100) NULL,
    original_signed_amount DECIMAL(18,2) NULL,
    target_stage_code VARCHAR(40) NULL,
    proposed_project_name VARCHAR(200) NULL,
    proposed_total_amount DECIMAL(18,2) NULL,
    change_reason VARCHAR(2000) NULL,
    review_opinion VARCHAR(2000) NULL,
    submitted_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    pending_project_id BINARY(16) GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN project_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_approval_one_pending (pending_project_id),
    KEY idx_project_approval_project_submitted (project_id, submitted_at),
    KEY idx_project_approval_approver_status_due (approver_id, status, due_at),
    CONSTRAINT fk_project_approval_project FOREIGN KEY (project_id) REFERENCES project_project (id),
    CONSTRAINT fk_project_approval_initiator FOREIGN KEY (initiator_id) REFERENCES identity_user_account (id),
    CONSTRAINT fk_project_approval_approver FOREIGN KEY (approver_id) REFERENCES identity_user_account (id),
    CONSTRAINT chk_project_approval_major_change_fields CHECK (
        request_type <> 'MAJOR_CHANGE' OR (change_type IS NOT NULL AND change_reason IS NOT NULL)
    ),
    CONSTRAINT chk_project_approval_amount_non_negative CHECK (proposed_total_amount IS NULL OR proposed_total_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_approval_action (
    id BINARY(16) NOT NULL,
    request_id BINARY(16) NOT NULL,
    action_code VARCHAR(40) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    action_at DATETIME(6) NOT NULL,
    opinion VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    KEY idx_project_approval_action_request (request_id, action_at),
    CONSTRAINT fk_project_approval_action_request FOREIGN KEY (request_id) REFERENCES project_approval_request (id),
    CONSTRAINT fk_project_approval_action_actor FOREIGN KEY (actor_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
