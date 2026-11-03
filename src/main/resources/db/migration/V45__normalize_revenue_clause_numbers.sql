-- 数字编号的唯一性按去除前导零后的完整文本判断，避免大整数溢出。
-- 如旧数据存在归一化冲突，原子建索引失败，不覆盖或删除计收及审核记录。
ALTER TABLE revenue_entry
 ADD COLUMN clause_number_key VARCHAR(200) COLLATE utf8mb4_bin
 GENERATED ALWAYS AS (
   CASE WHEN CHAR_LENGTH(clause_no)>0 AND CHAR_LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(clause_no, '0', ''), '1', ''), '2', ''), '3', ''), '4', ''), '5', ''), '6', ''), '7', ''), '8', ''), '9', ''))=0
     THEN COALESCE(NULLIF(TRIM(LEADING '0' FROM clause_no), ''), '0')
     ELSE clause_no END
 ) STORED,
 DROP INDEX uk_revenue_business,
 ADD CONSTRAINT uk_revenue_business UNIQUE(account_month, contract_no, clause_number_key, business_number_hash);

-- 同步主档字段与来源展示值，不改变认定、审批、版本和业务更新时间。
UPDATE revenue_entry
 SET clause_no=clause_number_key,
     source_fields=JSON_SET(source_fields, '$."条款编号"', clause_number_key)
 WHERE clause_no<>clause_number_key
    OR NOT (JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."条款编号"')) <=> clause_number_key);
