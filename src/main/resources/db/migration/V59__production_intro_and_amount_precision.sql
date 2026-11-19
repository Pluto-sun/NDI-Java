ALTER TABLE production_plan
    ADD COLUMN product_introduction VARCHAR(2000) NULL AFTER project_status,
    MODIFY COLUMN network_order_amount DECIMAL(20,4) NULL,
    MODIFY COLUMN gewu_order_amount DECIMAL(20,4) NULL,
    MODIFY COLUMN gewu_revenue_amount DECIMAL(20,4) NULL,
    MODIFY COLUMN big_data_order_amount DECIMAL(20,4) NULL,
    MODIFY COLUMN big_data_revenue_amount DECIMAL(20,4) NULL,
    MODIFY COLUMN wangwu_order_amount DECIMAL(20,4) NULL,
    MODIFY COLUMN wangwu_revenue_amount DECIMAL(20,4) NULL;
