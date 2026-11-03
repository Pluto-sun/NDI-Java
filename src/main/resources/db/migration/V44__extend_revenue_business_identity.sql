-- 对完整业务号码取摘要参与索引，保留来源文本及其现有长度上限。
-- 缺少该选填字段的历史记录按空字符串参与判重。
ALTER TABLE revenue_entry
 ADD COLUMN business_number_hash BINARY(32)
 GENERATED ALWAYS AS (
   UNHEX(SHA2(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."业务号码/项目编号"')), ''), 256))
 ) STORED,
 DROP INDEX uk_revenue_business,
 ADD CONSTRAINT uk_revenue_business UNIQUE(account_month, contract_no, clause_no, business_number_hash);
