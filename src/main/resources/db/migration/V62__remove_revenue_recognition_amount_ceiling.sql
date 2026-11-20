-- 保留历史零金额认定；新增认定由服务层校验为正数。
ALTER TABLE revenue_entry
    DROP CHECK ck_revenue_recognition,
    ADD CONSTRAINT ck_revenue_recognition CHECK (recognized_amount IS NULL OR recognized_amount >= 0);
