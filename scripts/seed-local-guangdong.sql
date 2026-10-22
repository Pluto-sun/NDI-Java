-- 仅供本地 LOCAL_ACTOR 联调，不配置共享密码，不覆盖已有账号或组织。
START TRANSACTION;
INSERT INTO organization_province (id, province_code, province_name, enabled)
SELECT UUID_TO_BIN(UUID()), '440000', '广东省', TRUE
WHERE NOT EXISTS (SELECT 1 FROM organization_province WHERE province_name IN ('广东', '广东省') OR province_code = '440000');

SET @gd = (SELECT id FROM organization_province WHERE province_name IN ('广东', '广东省') AND enabled = TRUE ORDER BY province_code LIMIT 1);

INSERT INTO organization_city (id, province_id, city_code, city_name, enabled)
SELECT UUID_TO_BIN(UUID()), @gd, source.code, source.name, TRUE
FROM (
    SELECT '440100' code, '广州市' name UNION ALL SELECT '440200', '韶关市'
    UNION ALL SELECT '440300', '深圳市' UNION ALL SELECT '440400', '珠海市'
    UNION ALL SELECT '440500', '汕头市' UNION ALL SELECT '440600', '佛山市'
    UNION ALL SELECT '440700', '江门市' UNION ALL SELECT '440800', '湛江市'
    UNION ALL SELECT '440900', '茂名市' UNION ALL SELECT '441200', '肇庆市'
    UNION ALL SELECT '441300', '惠州市' UNION ALL SELECT '441400', '梅州市'
    UNION ALL SELECT '441500', '汕尾市' UNION ALL SELECT '441600', '河源市'
    UNION ALL SELECT '441700', '阳江市' UNION ALL SELECT '441800', '清远市'
    UNION ALL SELECT '441900', '东莞市' UNION ALL SELECT '442000', '中山市'
    UNION ALL SELECT '445100', '潮州市' UNION ALL SELECT '445200', '揭阳市'
    UNION ALL SELECT '445300', '云浮市'
) source
WHERE @gd IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM organization_city city WHERE city.city_code = source.code
       OR (city.province_id = @gd AND city.city_name IN (source.name, REPLACE(source.name, '市', '')))
);

SET @gz = (SELECT id FROM organization_city WHERE province_id = @gd AND city_name IN ('广州', '广州市') AND enabled = TRUE LIMIT 1);
INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled)
SELECT UUID_TO_BIN(UUID()), source.login, source.name,
       IF(source.scope IN ('PROVINCE', 'CITY', 'REVIEWER'), @gd, NULL),
       IF(source.scope = 'CITY', @gz, NULL), TRUE
FROM (
    SELECT 'local_gd_province' login, '广东省分填报人' name, 'PROVINCE' scope
    UNION ALL SELECT 'local_gd_digital', '广东数字化负责人（演示）', 'PROVINCE'
    UNION ALL SELECT 'local_gd_intelligent', '广东智能化负责人（演示）', 'PROVINCE'
    UNION ALL SELECT 'local_gd_city', '广州地市负责人（演示）', 'CITY'
    UNION ALL SELECT 'local_gd_reviewer', '广东接口人（演示）', 'REVIEWER'
    UNION ALL SELECT 'local_group', '集团（演示）', 'NATIONAL'
    UNION ALL SELECT 'local_ds_tech', '数科（演示）', 'NATIONAL'
    UNION ALL SELECT 'local_ds_intelligence', '数智（演示）', 'NATIONAL'
    UNION ALL SELECT 'local_gd_dpass', '广东 DPASS（演示）', 'NATIONAL'
    UNION ALL SELECT 'local_admin', '系统管理员（演示）', 'NATIONAL'
) source WHERE @gd IS NOT NULL AND @gz IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM identity_user_account account WHERE account.login_name = source.login);

INSERT IGNORE INTO identity_user_role (user_id, role_code)
SELECT account.id, source.role FROM (
    SELECT 'local_gd_province' login, 'PROVINCE_REPORTER' role
    UNION ALL SELECT 'local_gd_digital', 'PROVINCE_DIGITAL_LEADER'
    UNION ALL SELECT 'local_gd_intelligent', 'PROVINCE_INTELLIGENT_LEADER'
    UNION ALL SELECT 'local_gd_city', 'CITY_REPORTER'
    UNION ALL SELECT 'local_gd_reviewer', 'INTERFACE_REVIEWER'
    UNION ALL SELECT 'local_group', 'GROUP_REVIEWER'
    UNION ALL SELECT 'local_ds_tech', 'DS_TECH'
    UNION ALL SELECT 'local_ds_intelligence', 'DS_INTELLIGENCE'
    UNION ALL SELECT 'local_gd_dpass', 'GD_DPASS'
    UNION ALL SELECT 'local_admin', 'SYSTEM_ADMIN'
) source JOIN identity_user_account account ON account.login_name = source.login
-- 已存在的同名非演示账号不追加权限。
WHERE account.display_name LIKE '%（演示）' OR (account.login_name = 'local_gd_province' AND account.display_name = '广东省分填报人');

INSERT IGNORE INTO identity_reviewer_province (reviewer_id, province_id)
SELECT account.id, @gd FROM identity_user_account account
WHERE account.login_name = 'local_gd_reviewer' AND account.display_name = '广东接口人（演示）' AND @gd IS NOT NULL;
COMMIT;
