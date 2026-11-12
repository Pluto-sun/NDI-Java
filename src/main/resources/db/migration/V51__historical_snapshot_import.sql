CREATE TABLE project_snapshot_import_complete (
    month_start DATE PRIMARY KEY
);
CREATE TABLE snapshot_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE snapshot_import_task (
 actor_id BINARY(16) PRIMARY KEY, id BINARY(16) NOT NULL UNIQUE,
 bucket_name VARCHAR(255) NOT NULL, object_key VARCHAR(1000) NOT NULL,
 byte_size BIGINT NOT NULL, sha256_hex CHAR(64) NOT NULL,
 task_json JSON NOT NULL, updated_at TIMESTAMP(6) NOT NULL
);
