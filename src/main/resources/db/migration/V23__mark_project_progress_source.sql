ALTER TABLE project_progress
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'APPENDED';

UPDATE project_progress progress
JOIN project_project project ON project.id = progress.project_id
SET progress.source_type = 'INITIAL'
WHERE progress.occurred_at = project.created_at
  AND progress.corrects_progress_id IS NULL;

UPDATE project_progress
SET source_type = 'CORRECTED'
WHERE corrects_progress_id IS NOT NULL;

CREATE INDEX idx_project_progress_occurred_source
    ON project_progress (occurred_at, source_type);

CREATE INDEX idx_project_change_event_occurred
    ON project_change_event (occurred_at);
