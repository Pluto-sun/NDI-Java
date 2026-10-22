-- 仅供一次性浏览器验收库；历史事实直接造数，不提供业务人工补录入口。
START TRANSACTION;
INSERT INTO organization_city(id,province_id,city_code,city_name,enabled)
VALUES(UUID_TO_BIN('cccccccc-cccc-4ccc-8ccc-cccccccccccc'),UUID_TO_BIN('11111111-1111-4111-8111-111111111111'),'E2E_HISTORY_CITY','历史乙市',true);

INSERT INTO project_project(id,project_code,province_id,city_id,project_name,customer_name,industry_code,customer_need,construction_content_raw,total_amount,current_stage_code,
 province_leader_user_id,city_account_manager_name,version_no,created_by,created_at,updated_by,updated_at)
SELECT UUID_TO_BIN(CONCAT('dddddddd-dddd-4ddd-8ddd-ddddddddddd',n)),CONCAT('E2E-HISTORY-',n),
 UUID_TO_BIN('11111111-1111-4111-8111-111111111111'),
 UUID_TO_BIN(IF(n=1,'cccccccc-cccc-4ccc-8ccc-cccccccccccc','22222222-2222-4222-8222-222222222222')),
 CONCAT('隔离历史项目',n),'隔离历史客户','C25','历史维度验收','历史建设内容',n*110,'TERMINATED',
 UUID_TO_BIN('33333333-3333-4333-8333-333333333333'),'历史客户经理',0,
 UUID_TO_BIN('44444444-4444-4444-8444-444444444444'),'2020-01-01',
 UUID_TO_BIN('44444444-4444-4444-8444-444444444444'),'2020-03-01'
FROM (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3) numbers;

INSERT INTO project_monthly_snapshot(month_start,project_id,province_id,city_id,city_name,industry_code,industry_name,stage_code,total_amount,
 revision_no,updated_at,network_amount,gewu_amount,big_data_amount,wangwu_amount)
SELECT months.month_start,p.id,p.province_id,
 IF(months.month_start='2020-02-01' AND p.project_code='E2E-HISTORY-1',UUID_TO_BIN('cccccccc-cccc-4ccc-8ccc-cccccccccccc'),UUID_TO_BIN('22222222-2222-4222-8222-222222222222')),
 IF(months.month_start='2020-02-01' AND p.project_code='E2E-HISTORY-1','历史乙市','历史甲市'),
 IF(months.month_start='2020-02-01' AND p.project_code='E2E-HISTORY-1','C26','C25'),
 IF(months.month_start='2020-02-01' AND p.project_code='E2E-HISTORY-1','化学原料和化学制品制造业','石油、煤炭及其他燃料加工业'),
 CASE p.project_code WHEN 'E2E-HISTORY-3' THEN 'TERMINATED' WHEN 'E2E-HISTORY-2' THEN IF(months.month_start='2020-01-01','BIDDING','COLLECTION') ELSE IF(months.month_start='2020-01-01','SOLUTION_EXCHANGE','BIDDING') END,
 CAST(RIGHT(p.project_code,1) AS UNSIGNED)*months.factor,
 0,'2020-03-01',CAST(RIGHT(p.project_code,1) AS UNSIGNED)*months.factor*0.2,
 CAST(RIGHT(p.project_code,1) AS UNSIGNED)*months.factor*0.1,
 CAST(RIGHT(p.project_code,1) AS UNSIGNED)*months.factor*0.05,
 CAST(RIGHT(p.project_code,1) AS UNSIGNED)*months.factor*0.01
FROM project_project p CROSS JOIN (SELECT DATE('2020-01-01') month_start,100 factor UNION ALL SELECT DATE('2020-02-01'),110) months
WHERE p.project_code LIKE 'E2E-HISTORY-%';
UPDATE project_monthly_snapshot_control SET activation_month='2020-01-01',last_completed_month='2020-02-01' WHERE id=1;
COMMIT;
