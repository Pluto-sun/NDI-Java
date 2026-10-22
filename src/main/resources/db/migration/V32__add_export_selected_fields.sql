-- 旧任务空字段配置继续输出既定摘要列，新任务保存提交时的字段及顺序。
ALTER TABLE project_export_task ADD COLUMN selected_fields JSON NULL;
