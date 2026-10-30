CREATE TABLE revenue_entry (
 id BINARY(16) PRIMARY KEY, version_no BIGINT NOT NULL DEFAULT 0,
 account_month DATE NOT NULL, province_id BINARY(16), specific_organization BOOLEAN NOT NULL,
 contract_no VARCHAR(200) COLLATE utf8mb4_bin NOT NULL,
 clause_no VARCHAR(200) COLLATE utf8mb4_bin NOT NULL,
 clause_amount DECIMAL(18,2) NOT NULL, source_amount DECIMAL(18,2) NOT NULL,
 source_fields JSON NOT NULL, recognition JSON,
 recognized_amount DECIMAL(18,2), recognized_by BINARY(16), recognized_at TIMESTAMP(6),
 created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uk_revenue_business UNIQUE(account_month,contract_no,clause_no),
 CONSTRAINT fk_revenue_province FOREIGN KEY(province_id) REFERENCES organization_province(id),
 CONSTRAINT ck_revenue_recognition CHECK (recognized_amount IS NULL OR (recognized_amount>=0 AND recognized_amount<=clause_amount)),
 CONSTRAINT ck_revenue_region CHECK ((specific_organization=TRUE AND province_id IS NULL) OR (specific_organization=FALSE AND province_id IS NOT NULL)),
 INDEX ix_revenue_scope(province_id,account_month,created_at), INDEX ix_revenue_month(account_month,created_at)
);
CREATE TABLE revenue_recognition_request (
 id BINARY(16) PRIMARY KEY, entry_id BINARY(16) NOT NULL, initiator_id BINARY(16) NOT NULL,
 approver_id BINARY(16) NOT NULL, base_version BIGINT NOT NULL, version_no BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(20) NOT NULL, proposed JSON NOT NULL, opinion VARCHAR(2000),
 created_at TIMESTAMP(6) NOT NULL, decided_at TIMESTAMP(6),
 pending_slot TINYINT GENERATED ALWAYS AS (IF(status='PENDING',1,NULL)) STORED,
 CONSTRAINT fk_revenue_request_entry FOREIGN KEY(entry_id) REFERENCES revenue_entry(id),
 CONSTRAINT uk_revenue_pending UNIQUE(entry_id,pending_slot),
 INDEX ix_revenue_approver(approver_id,status,created_at)
);
CREATE TABLE revenue_recognition_history (
 id BINARY(16) PRIMARY KEY, entry_id BINARY(16) NOT NULL, actor_id BINARY(16) NOT NULL,
 request_id BINARY(16), before_value JSON, after_value JSON NOT NULL, occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_revenue_history_entry FOREIGN KEY(entry_id) REFERENCES revenue_entry(id),
 INDEX ix_revenue_history(entry_id,occurred_at)
);
CREATE TABLE revenue_notification (
 id BINARY(16) PRIMARY KEY, entry_id BINARY(16) NOT NULL, recipient_id BINARY(16) NOT NULL,
 request_id BINARY(16), kind VARCHAR(30) NOT NULL, message VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, read_at TIMESTAMP(6),
 INDEX ix_revenue_notice(recipient_id,read_at,created_at)
);
CREATE TABLE revenue_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE revenue_import_task (
 actor_id BINARY(16) PRIMARY KEY, id BINARY(16) NOT NULL UNIQUE,
 bucket_name VARCHAR(255) NOT NULL, object_key VARCHAR(1000) NOT NULL,
 byte_size BIGINT NOT NULL, sha256_hex CHAR(64) NOT NULL,
 task_json JSON NOT NULL, updated_at TIMESTAMP(6) NOT NULL
);
