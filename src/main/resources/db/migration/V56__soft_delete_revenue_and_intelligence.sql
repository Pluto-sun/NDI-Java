ALTER TABLE revenue_entry
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD COLUMN active_key TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    DROP INDEX uk_revenue_business,
    ADD UNIQUE KEY uk_revenue_business
        (account_month, contract_number_key, clause_number_key, business_number_hash,
         natural_customer_name_hash, product_code_hash, active_key);

ALTER TABLE intelligence_capability
    ADD COLUMN deleted_at TIMESTAMP(6) NULL,
    ADD COLUMN active_key TINYINT GENERATED ALWAYS AS (IF(deleted_at IS NULL, 1, NULL)) STORED,
    DROP INDEX uq_intelligence_capability_business_key,
    ADD UNIQUE KEY uq_intelligence_capability_business_key (kind, business_key, active_key);
