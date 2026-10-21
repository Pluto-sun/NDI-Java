CREATE TABLE identity_reviewer_province (
    reviewer_id BINARY(16) NOT NULL,
    province_id BINARY(16) NOT NULL,
    PRIMARY KEY (reviewer_id, province_id),
    KEY idx_reviewer_province_region (province_id, reviewer_id),
    CONSTRAINT fk_reviewer_province_user FOREIGN KEY (reviewer_id) REFERENCES identity_user_account (id),
    CONSTRAINT fk_reviewer_province_region FOREIGN KEY (province_id) REFERENCES organization_province (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 仅迁移已有有效单省接口人；迁移后权限只读映射，不再回退到账号属地。
INSERT INTO identity_reviewer_province (reviewer_id, province_id)
SELECT account.id, account.province_id
FROM identity_user_account account
JOIN organization_province province ON province.id = account.province_id AND province.enabled = TRUE
WHERE account.enabled = TRUE
  AND EXISTS (SELECT 1 FROM identity_user_role role WHERE role.user_id = account.id AND role.role_code = 'INTERFACE_REVIEWER')
  AND NOT EXISTS (SELECT 1 FROM identity_user_role role WHERE role.user_id = account.id AND role.role_code = 'SYSTEM_ADMIN');

-- 导出任务冻结完整授权集合，映射调整后旧文件下载必须重新授权。
ALTER TABLE project_export_task ADD COLUMN scope_province_ids JSON NULL;
