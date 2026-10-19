CREATE TABLE document_object (
    id BINARY(16) NOT NULL,
    scope_type VARCHAR(40) NOT NULL,
    scope_id BINARY(16) NOT NULL,
    purpose_code VARCHAR(40) NOT NULL,
    bucket_name VARCHAR(63) NOT NULL,
    staging_object_key VARCHAR(255) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256_hex VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    uploaded_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    storage_etag VARCHAR(150) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_object_staging_key (staging_object_key),
    UNIQUE KEY uk_document_object_key (object_key),
    KEY idx_document_object_scope (scope_type, scope_id, status),
    CONSTRAINT chk_document_object_byte_size CHECK (byte_size > 0),
    CONSTRAINT fk_document_object_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE project_approval_request
    ADD COLUMN evidence_document_id BINARY(16) NULL,
    ADD COLUMN stage_occurred_at DATETIME(6) NULL,
    ADD COLUMN proposed_contract_no VARCHAR(100) NULL,
    ADD COLUMN proposed_signed_amount DECIMAL(18,2) NULL,
    ADD COLUMN proposed_progress_content TEXT NULL,
    ADD COLUMN proposed_next_action TEXT NULL,
    ADD KEY idx_project_approval_evidence (evidence_document_id),
    ADD CONSTRAINT fk_project_approval_evidence FOREIGN KEY (evidence_document_id) REFERENCES document_object (id),
    ADD CONSTRAINT chk_project_approval_signed_amount CHECK (proposed_signed_amount IS NULL OR proposed_signed_amount >= 0),
    ADD CONSTRAINT chk_project_approval_stage_fields CHECK (
        request_type <> 'STAGE_ADVANCEMENT' OR (change_type IS NULL AND target_stage_code IS NOT NULL AND stage_occurred_at IS NOT NULL)
    );
