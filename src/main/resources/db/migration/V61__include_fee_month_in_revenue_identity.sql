-- 费用账期沿用来源字段，兼容历史紧凑月份和空值占位。
ALTER TABLE revenue_entry
    ADD COLUMN fee_month_key VARCHAR(7) COLLATE utf8mb4_bin GENERATED ALWAYS AS (
        CASE CHAR_LENGTH(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."费用账期"'))))
            WHEN 6 THEN INSERT(TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."费用账期"'))), 5, 0, '-')
            WHEN 7 THEN TRIM(JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."费用账期"')))
            ELSE ''
        END
    ) STORED,
    DROP INDEX uk_revenue_business,
    ADD UNIQUE KEY uk_revenue_business
        (account_month, contract_number_key, clause_number_key, business_number_hash,
         natural_customer_name_hash, product_code_hash, fee_month_key, active_key);
