-- 每账号只保留一项未完成向导，导入批次另作审计与来源追溯。
CREATE TABLE project_import_actor_lock (
 actor_id BINARY(16) PRIMARY KEY,
 CONSTRAINT fk_import_lock_actor FOREIGN KEY(actor_id) REFERENCES identity_user_account(id)
);
CREATE TABLE project_import_task (
 id BINARY(16) PRIMARY KEY,
 actor_id BINARY(16) NOT NULL UNIQUE,
 version_no BIGINT NOT NULL,
 step_no INT NOT NULL,
 file_name VARCHAR(255) NOT NULL,
 bucket_name VARCHAR(100) NOT NULL,
 object_key VARCHAR(500) NOT NULL,
 byte_size BIGINT NOT NULL,
 sha256_hex CHAR(64) NOT NULL,
 inspection_json JSON NOT NULL,
 sheet_name VARCHAR(31),
 header_row_number INT,
 field_mapping JSON NOT NULL,
 selected_rows JSON,
 preview_configuration JSON,
 batch_id BINARY(16),
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT ck_import_task_step CHECK(step_no BETWEEN 1 AND 3),
 CONSTRAINT fk_import_task_actor FOREIGN KEY(actor_id) REFERENCES identity_user_account(id),
 CONSTRAINT fk_import_task_batch FOREIGN KEY(batch_id) REFERENCES project_import_batch(id)
);
ALTER TABLE project_import_row ADD name_review JSON NULL;
