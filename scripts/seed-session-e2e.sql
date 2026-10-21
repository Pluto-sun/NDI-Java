-- 仅由隔离浏览器验收脚本调用，禁止用于业务数据库。
-- 固定测试口令只覆盖固定验收账号；本地演示和正式初始化均不设置默认密码。
UPDATE identity_user_account
SET password_hash = '{pbkdf2}34b9b012f04b97dd81d855f581dced016b042f01b3fdd22b03d4db038a3e39322b8453e8056ade651059ee731e0f1cf7'
WHERE login_name IN ('local_province', 'local_city', 'local_reviewer', 'e2e_admin', 'e2e_group');
