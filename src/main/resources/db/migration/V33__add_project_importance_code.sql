-- 重要性保留稳定代码，不在未定版前引入等级枚举或审批规则。
ALTER TABLE project_project ADD COLUMN importance_code VARCHAR(30) NOT NULL DEFAULT 'NORMAL';
