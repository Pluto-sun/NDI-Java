-- 原金额作为预计下单金额保留，新增计收分项默认 NULL。
ALTER TABLE production_plan
    RENAME COLUMN network_amount TO network_order_amount,
    RENAME COLUMN gewu_amount TO gewu_order_amount,
    RENAME COLUMN big_data_amount TO big_data_order_amount,
    RENAME COLUMN wangwu_amount TO wangwu_order_amount,
    ADD COLUMN gewu_revenue_amount DECIMAL(18,2) NULL,
    ADD COLUMN big_data_revenue_amount DECIMAL(18,2) NULL,
    ADD COLUMN wangwu_revenue_amount DECIMAL(18,2) NULL;
