ALTER TABLE production_plan ADD COLUMN remark VARCHAR(2000) NULL AFTER project_status;

-- 历史零金额记录保留，列表分类按金额重新生成。
UPDATE production_plan SET categories = JSON_MERGE_PRESERVE(
    IF(network_order_amount > 0, JSON_ARRAY('NETWORK'), JSON_ARRAY()),
    IF(gewu_order_amount > 0 OR gewu_revenue_amount > 0, JSON_ARRAY('GEWU'), JSON_ARRAY()),
    IF(big_data_order_amount > 0 OR big_data_revenue_amount > 0, JSON_ARRAY('BIG_DATA'), JSON_ARRAY()),
    IF(wangwu_order_amount > 0 OR wangwu_revenue_amount > 0, JSON_ARRAY('WANGWU'), JSON_ARRAY())
);
