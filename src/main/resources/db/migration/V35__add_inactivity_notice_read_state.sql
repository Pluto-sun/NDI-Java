-- 阅读状态属于个人通知，不改变督办处理状态；存量待处理通知保持未读。
ALTER TABLE project_inactivity_notice ADD COLUMN read_at DATETIME(6) NULL;
