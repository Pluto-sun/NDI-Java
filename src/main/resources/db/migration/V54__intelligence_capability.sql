CREATE TABLE intelligence_capability (
 id BINARY(16) PRIMARY KEY, kind VARCHAR(24) NOT NULL,
 province_id BINARY(16) NOT NULL, city_id BINARY(16),
 version_updated_date DATE, details JSON NOT NULL, version_no BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (province_id) REFERENCES organization_province(id),
 FOREIGN KEY (city_id) REFERENCES organization_city(id),
 INDEX ix_intelligence_scope(kind,province_id,city_id,updated_at)
);
CREATE TABLE intelligence_token (
 id BINARY(16) PRIMARY KEY, agent_id BINARY(16) NOT NULL,
 metric_month CHAR(7) NOT NULL, amount DECIMAL(20,4) NOT NULL,
 screenshot_id BINARY(16),
 FOREIGN KEY(agent_id) REFERENCES intelligence_capability(id),
 UNIQUE KEY uk_agent_month(agent_id,metric_month)
);
CREATE TABLE wangwu_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE wangwu_import_task LIKE capability_import_task;
CREATE TABLE agents_live_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE agents_live_import_task LIKE capability_import_task;
CREATE TABLE agents_development_import_lock (actor_id BINARY(16) PRIMARY KEY);
CREATE TABLE agents_development_import_task LIKE capability_import_task;
