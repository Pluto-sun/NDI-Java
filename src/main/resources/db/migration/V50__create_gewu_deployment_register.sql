CREATE TABLE capability_gewu_deployment (
    id BINARY(16) PRIMARY KEY,
    province_id BINARY(16) NOT NULL,
    city_id BINARY(16) NOT NULL,
    details JSON NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    FOREIGN KEY (province_id) REFERENCES organization_province(id),
    FOREIGN KEY (city_id) REFERENCES organization_city(id),
    INDEX ix_gewu_deployment_scope (province_id, city_id, deleted_at, updated_at)
);
CREATE TABLE capability_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE capability_import_task (
    actor_id BINARY(16) PRIMARY KEY, id BINARY(16) NOT NULL UNIQUE,
    bucket_name VARCHAR(255) NOT NULL, object_key VARCHAR(1000) NOT NULL,
    byte_size BIGINT NOT NULL, sha256_hex CHAR(64) NOT NULL,
    task_json JSON NOT NULL, updated_at TIMESTAMP(6) NOT NULL
);
