ALTER TABLE project_import_batch
    ADD COLUMN sheet_name VARCHAR(31) NULL,
    ADD COLUMN header_row_number INT NULL,
    ADD COLUMN field_mapping JSON NULL;
