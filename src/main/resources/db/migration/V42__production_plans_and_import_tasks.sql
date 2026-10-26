CREATE TABLE production_plan (
 id BINARY(16) PRIMARY KEY,
 version_no BIGINT NOT NULL DEFAULT 0,
 reviewer_name VARCHAR(100) NOT NULL,
 plan_month DATE NOT NULL,
 province_id BINARY(16) NOT NULL,
 city_id BINARY(16) NOT NULL,
 project_name VARCHAR(300) NOT NULL,
 customer_name VARCHAR(300) NOT NULL,
 normalized_project VARCHAR(300) COLLATE utf8mb4_bin NOT NULL,
 normalized_customer VARCHAR(300) COLLATE utf8mb4_bin NOT NULL,
 categories JSON NOT NULL,
 network_amount DECIMAL(18,2), gewu_amount DECIMAL(18,2), big_data_amount DECIMAL(18,2), wangwu_amount DECIMAL(18,2),
 project_status VARCHAR(200),
 created_by BINARY(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 deleted_at TIMESTAMP(6),
 active_slot TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL,1,NULL)) STORED,
 CONSTRAINT fk_production_province FOREIGN KEY(province_id) REFERENCES organization_province(id),
 CONSTRAINT fk_production_city FOREIGN KEY(city_id) REFERENCES organization_city(id),
 CONSTRAINT uk_production_business UNIQUE(plan_month,province_id,city_id,normalized_project,normalized_customer,active_slot),
 INDEX ix_production_scope(province_id,city_id,plan_month),
 INDEX ix_production_month(plan_month,created_at)
);
CREATE TABLE production_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE production_import_task (
 actor_id BINARY(16) PRIMARY KEY, id BINARY(16) NOT NULL UNIQUE,
 bucket_name VARCHAR(255) NOT NULL, object_key VARCHAR(1000) NOT NULL,
 byte_size BIGINT NOT NULL, sha256_hex CHAR(64) NOT NULL,
 task_json JSON NOT NULL, updated_at TIMESTAMP(6) NOT NULL
);
