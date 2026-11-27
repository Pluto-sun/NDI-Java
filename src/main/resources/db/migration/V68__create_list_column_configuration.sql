CREATE TABLE list_column_configuration (
    list_id VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    fixed_fields JSON NOT NULL,
    CONSTRAINT ck_list_column_configuration_array CHECK (JSON_TYPE(fixed_fields) = 'ARRAY')
);
