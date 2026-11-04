-- 缺失、空白和--统一为空字符串；先原子验证新索引，冲突时不删除或合并记录。
ALTER TABLE revenue_entry
 ADD COLUMN contract_number_key VARCHAR(200) COLLATE utf8mb4_bin
 GENERATED ALWAYS AS (COALESCE(NULLIF(TRIM(contract_no), '--'), '')) STORED,
 MODIFY COLUMN clause_number_key VARCHAR(200) COLLATE utf8mb4_bin
 GENERATED ALWAYS AS (CASE WHEN CHAR_LENGTH(COALESCE(NULLIF(TRIM(clause_no), '--'), ''))>0 AND CHAR_LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(NULLIF(TRIM(clause_no), '--'), ''), '0', ''), '1', ''), '2', ''), '3', ''), '4', ''), '5', ''), '6', ''), '7', ''), '8', ''), '9', ''))=0 THEN COALESCE(NULLIF(TRIM(LEADING '0' FROM COALESCE(NULLIF(TRIM(clause_no), '--'), '')), ''), '0') ELSE COALESCE(NULLIF(TRIM(clause_no), '--'), '') END) STORED,
 MODIFY COLUMN business_number_hash BINARY(32)
 GENERATED ALWAYS AS (UNHEX(SHA2(CASE WHEN JSON_TYPE(JSON_EXTRACT(source_fields, '$."业务号码/项目编号"'))='NULL' THEN '' ELSE COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."业务号码/项目编号"'))), '--'), '') END, 256))) STORED,
 MODIFY COLUMN natural_customer_name_hash BINARY(32)
 GENERATED ALWAYS AS (UNHEX(SHA2(CASE WHEN JSON_TYPE(JSON_EXTRACT(source_fields, '$."自然客户名称"'))='NULL' THEN '' ELSE COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."自然客户名称"'))), '--'), '') END, 256))) STORED,
 MODIFY COLUMN product_code_hash BINARY(32)
 GENERATED ALWAYS AS (UNHEX(SHA2(CASE WHEN JSON_TYPE(JSON_EXTRACT(source_fields, '$."产品编码"'))='NULL' THEN '' ELSE COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."产品编码"'))), '--'), '') END, 256))) STORED,
 DROP INDEX uk_revenue_business,
 ADD CONSTRAINT uk_revenue_business UNIQUE(account_month, contract_number_key, clause_number_key, business_number_hash, natural_customer_name_hash, product_code_hash);

-- 规范化来源展示字段，不改变版本、认定、审批及业务更新时间。
UPDATE revenue_entry
 SET contract_no=contract_number_key,
     clause_no=clause_number_key,
     source_fields=JSON_SET(source_fields,
       '$."合同编号"', contract_number_key,
       '$."条款编号"', clause_number_key,
       '$."业务号码/项目编号"', CASE WHEN JSON_TYPE(JSON_EXTRACT(source_fields, '$."业务号码/项目编号"'))='NULL' THEN '' ELSE COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."业务号码/项目编号"'))), '--'), '') END,
       '$."自然客户名称"', CASE WHEN JSON_TYPE(JSON_EXTRACT(source_fields, '$."自然客户名称"'))='NULL' THEN '' ELSE COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."自然客户名称"'))), '--'), '') END,
       '$."产品编码"', CASE WHEN JSON_TYPE(JSON_EXTRACT(source_fields, '$."产品编码"'))='NULL' THEN '' ELSE COALESCE(NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."产品编码"'))), '--'), '') END);
