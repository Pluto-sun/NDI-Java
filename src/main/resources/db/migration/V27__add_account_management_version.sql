-- 账号资料、密码和管理员编辑共用版本，阻止陈旧表单覆盖新配置。
ALTER TABLE identity_user_account ADD COLUMN management_version BIGINT NOT NULL DEFAULT 0;
