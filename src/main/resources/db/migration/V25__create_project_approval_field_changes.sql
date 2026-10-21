CREATE TABLE project_approval_field_change (
    request_id BINARY(16) NOT NULL,
    sort_order INT NOT NULL,
    field_code VARCHAR(40) NOT NULL,
    original_value VARCHAR(200) NULL,
    proposed_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (request_id, sort_order),
    UNIQUE KEY uk_project_approval_change_field (request_id, field_code),
    CONSTRAINT fk_project_approval_change_field_request FOREIGN KEY (request_id)
        REFERENCES project_approval_request (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 旧修订缺少分类前后值，保留为空，避免把未知历史金额伪装为零。
ALTER TABLE project_monthly_snapshot_revision
    ADD COLUMN before_network_amount DECIMAL(18,2) NULL,
    ADD COLUMN after_network_amount DECIMAL(18,2) NULL,
    ADD COLUMN before_gewu_amount DECIMAL(18,2) NULL,
    ADD COLUMN after_gewu_amount DECIMAL(18,2) NULL,
    ADD COLUMN before_big_data_amount DECIMAL(18,2) NULL,
    ADD COLUMN after_big_data_amount DECIMAL(18,2) NULL,
    ADD COLUMN before_wangwu_amount DECIMAL(18,2) NULL,
    ADD COLUMN after_wangwu_amount DECIMAL(18,2) NULL;

-- 相同数据库时间精度下按实际写入次序重放，不使用随机标识决定审批先后。
ALTER TABLE project_monthly_state_event
    ADD COLUMN event_sequence BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE KEY uk_project_monthly_event_sequence (event_sequence);
