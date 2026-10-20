CREATE INDEX idx_project_import_batch_actor_created
    ON project_import_batch (actor_id, created_at DESC, id DESC);
