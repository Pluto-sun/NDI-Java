-- 与固定演示账号共同用于隔离浏览器验收库，不写入本地业务库。
INSERT INTO identity_user_account (id, login_name, display_name, enabled)
VALUES
    (UUID_TO_BIN('66666666-6666-4666-8666-666666666666'), 'e2e_admin', '验收管理员', true),
    (UUID_TO_BIN('77777777-7777-4777-8777-777777777777'), 'e2e_group', '验收集团用户', true);

INSERT INTO identity_user_role (user_id, role_code)
VALUES
    (UUID_TO_BIN('66666666-6666-4666-8666-666666666666'), 'SYSTEM_ADMIN'),
    (UUID_TO_BIN('77777777-7777-4777-8777-777777777777'), 'GROUP_REVIEWER');
