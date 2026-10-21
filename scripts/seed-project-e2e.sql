-- 与固定演示账号共同用于隔离浏览器验收库，不写入本地业务库。
INSERT INTO organization_province (id,province_code,province_name,enabled) VALUES
    (UUID_TO_BIN('88888888-8888-4888-8888-888888888888'),'E2E_REVIEWER_B','浙江省',true),
    (UUID_TO_BIN('99999999-9999-4999-8999-999999999999'),'E2E_REVIEWER_C','山东省',true);
INSERT INTO organization_city (id,province_id,city_code,city_name,enabled) VALUES
    (UUID_TO_BIN('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),UUID_TO_BIN('88888888-8888-4888-8888-888888888888'),'E2E_SCOPE_CITY_B','范围验收乙市',true),
    (UUID_TO_BIN('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),UUID_TO_BIN('99999999-9999-4999-8999-999999999999'),'E2E_SCOPE_CITY_C','范围验收丙市',true);
INSERT INTO identity_user_account (id, login_name, display_name, enabled)
VALUES
    (UUID_TO_BIN('66666666-6666-4666-8666-666666666666'), 'e2e_admin', '验收管理员', true),
    (UUID_TO_BIN('77777777-7777-4777-8777-777777777777'), 'e2e_group', '验收集团用户', true);

INSERT INTO identity_user_role (user_id, role_code)
VALUES
    (UUID_TO_BIN('66666666-6666-4666-8666-666666666666'), 'SYSTEM_ADMIN'),
    (UUID_TO_BIN('77777777-7777-4777-8777-777777777777'), 'GROUP_REVIEWER');
