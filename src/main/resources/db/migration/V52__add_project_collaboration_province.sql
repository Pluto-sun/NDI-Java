ALTER TABLE project_project
    ADD COLUMN collaboration_province_id BINARY(16) NULL,
    ADD CONSTRAINT fk_project_collaboration_province
        FOREIGN KEY (collaboration_province_id) REFERENCES organization_province (id);
