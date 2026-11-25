ALTER TABLE revenue_entry
    ADD COLUMN source_organization_key BINARY(32) NULL,
    ADD INDEX ix_revenue_source_organization (source_organization_key, active_key);

CREATE TABLE revenue_organization_province (
    organization_key BINARY(32) NOT NULL PRIMARY KEY,
    province_id BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_revenue_organization_province FOREIGN KEY (province_id) REFERENCES organization_province (id),
    INDEX ix_revenue_organization_province (province_id)
);
