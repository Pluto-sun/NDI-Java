-- 仅供本地联调，使用固定 UUID 保证重复执行不会产生重复账号。
INSERT IGNORE INTO organization_province (id, province_code, province_name, enabled)
VALUES (UUID_TO_BIN('11111111-1111-4111-8111-111111111111'), 'LOCAL_DEMO', '本地演示省', TRUE);

INSERT IGNORE INTO organization_city (id, province_id, city_code, city_name, enabled)
VALUES (UUID_TO_BIN('22222222-2222-4222-8222-222222222222'),
        UUID_TO_BIN('11111111-1111-4111-8111-111111111111'), 'LOCAL_DEMO_CITY', '本地演示市', TRUE);

INSERT IGNORE INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled)
VALUES
    (UUID_TO_BIN('33333333-3333-4333-8333-333333333333'), 'local_province', '演示省分负责人',
     UUID_TO_BIN('11111111-1111-4111-8111-111111111111'), NULL, TRUE),
    (UUID_TO_BIN('44444444-4444-4444-8444-444444444444'), 'local_city', '演示地市负责人',
     UUID_TO_BIN('11111111-1111-4111-8111-111111111111'),
     UUID_TO_BIN('22222222-2222-4222-8222-222222222222'), TRUE),
    (UUID_TO_BIN('55555555-5555-4555-8555-555555555555'), 'local_reviewer', '演示省分接口人',
     UUID_TO_BIN('11111111-1111-4111-8111-111111111111'), NULL, TRUE);

INSERT IGNORE INTO identity_user_role (user_id, role_code)
VALUES
    (UUID_TO_BIN('33333333-3333-4333-8333-333333333333'), 'PROVINCE_REPORTER'),
    (UUID_TO_BIN('44444444-4444-4444-8444-444444444444'), 'CITY_REPORTER'),
    (UUID_TO_BIN('55555555-5555-4555-8555-555555555555'), 'INTERFACE_REVIEWER');

INSERT IGNORE INTO identity_reviewer_province (reviewer_id, province_id)
VALUES (UUID_TO_BIN('55555555-5555-4555-8555-555555555555'), UUID_TO_BIN('11111111-1111-4111-8111-111111111111'));

-- 仅更新固定演示 UUID 的显示名，修复旧版客户端字符集设置造成的乱码。
UPDATE organization_province SET province_name = '本地演示省'
WHERE id = UUID_TO_BIN('11111111-1111-4111-8111-111111111111');
UPDATE organization_city SET city_name = '本地演示市'
WHERE id = UUID_TO_BIN('22222222-2222-4222-8222-222222222222');
UPDATE identity_user_account SET display_name = '演示省分负责人'
WHERE id = UUID_TO_BIN('33333333-3333-4333-8333-333333333333');
UPDATE identity_user_account SET display_name = '演示地市负责人'
WHERE id = UUID_TO_BIN('44444444-4444-4444-8444-444444444444');
UPDATE identity_user_account SET display_name = '演示省分接口人'
WHERE id = UUID_TO_BIN('55555555-5555-4555-8555-555555555555');
