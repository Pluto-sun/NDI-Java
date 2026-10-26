-- 上线前检查存量冲突；发现冲突必须先明确交接归属，禁止静默删除。
ALTER TABLE identity_reviewer_province ADD CONSTRAINT uk_reviewer_province UNIQUE (province_id);
