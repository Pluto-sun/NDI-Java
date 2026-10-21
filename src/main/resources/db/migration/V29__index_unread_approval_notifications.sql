ALTER TABLE project_approval_notification
    ADD KEY idx_approval_notification_unread (recipient_id, read_at, request_id);
