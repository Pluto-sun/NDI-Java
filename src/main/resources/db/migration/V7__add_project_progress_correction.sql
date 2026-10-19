ALTER TABLE project_progress
    ADD COLUMN corrects_progress_id BINARY(16) NULL,
    ADD UNIQUE KEY uk_project_progress_project_id (project_id, id),
    ADD KEY idx_project_progress_correction (project_id, corrects_progress_id);

ALTER TABLE project_progress
    ADD CONSTRAINT fk_project_progress_correction_same_project
        FOREIGN KEY (project_id, corrects_progress_id)
        REFERENCES project_progress (project_id, id);
