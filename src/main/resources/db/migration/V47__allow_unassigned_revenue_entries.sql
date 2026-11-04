-- 未归属记录仅面向全国查看角色，由接口人直接认定，不授予省市账号权限。
-- 合同与条款的空值以空字符串保存，继续参与现有六字段唯一索引。
ALTER TABLE revenue_entry
 DROP CHECK ck_revenue_region,
 ADD CONSTRAINT ck_revenue_region CHECK (specific_organization=FALSE OR province_id IS NULL);
