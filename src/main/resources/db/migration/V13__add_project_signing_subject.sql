ALTER TABLE project_project
    ADD COLUMN signing_subject_code VARCHAR(20) NOT NULL DEFAULT 'PROVINCE_SIGN',
    ADD CONSTRAINT chk_project_project_signing_subject
        CHECK (signing_subject_code IN ('PROVINCE_SIGN', 'DS_SIGN'));
