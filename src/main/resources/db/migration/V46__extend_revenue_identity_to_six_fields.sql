-- 独立摘要保留完整文本及大小写，不受索引字符长度限制。
-- 新增选填字段的缺失值按空字符串参与判重，保留历史数据及认定、审核记录。
ALTER TABLE revenue_entry
 ADD COLUMN natural_customer_name_hash BINARY(32)
 GENERATED ALWAYS AS (
   UNHEX(SHA2(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."自然客户名称"')), ''), 256))
 ) STORED,
 ADD COLUMN product_code_hash BINARY(32)
 GENERATED ALWAYS AS (
   UNHEX(SHA2(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."产品编码"')), ''), 256))
 ) STORED,
 DROP INDEX uk_revenue_business,
 ADD CONSTRAINT uk_revenue_business UNIQUE(account_month, contract_no, clause_number_key, business_number_hash, natural_customer_name_hash, product_code_hash);
