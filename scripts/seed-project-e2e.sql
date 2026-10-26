-- 与固定演示账号共同用于隔离浏览器验收库，不写入本地业务库。
-- 隔离空库中的标准省份尚无引用，仅将其标识固定为测试夹具，避免重复省名。
SET @e2e_zhejiang_id=(SELECT id FROM organization_province WHERE province_code='330000');
SET @e2e_shandong_id=(SELECT id FROM organization_province WHERE province_code='370000');
-- 全国地市预置后已有父省引用，仅在隔离测试库中同步固定父子夹具标识。
SET FOREIGN_KEY_CHECKS=0;
UPDATE organization_province SET id=UUID_TO_BIN('88888888-8888-4888-8888-888888888888') WHERE province_code='330000';
UPDATE organization_province SET id=UUID_TO_BIN('99999999-9999-4999-8999-999999999999') WHERE province_code='370000';
UPDATE organization_city SET province_id=UUID_TO_BIN('88888888-8888-4888-8888-888888888888') WHERE province_id=@e2e_zhejiang_id;
UPDATE organization_city SET province_id=UUID_TO_BIN('99999999-9999-4999-8999-999999999999') WHERE province_id=@e2e_shandong_id;
SET FOREIGN_KEY_CHECKS=1;
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
