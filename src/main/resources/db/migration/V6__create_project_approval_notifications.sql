ALTER TABLE project_approval_request
    ADD KEY idx_project_approval_pending_due (status, due_at, id);

CREATE TABLE project_approval_notification (
    id BINARY(16) NOT NULL,
    request_id BINARY(16) NOT NULL,
    recipient_id BINARY(16) NOT NULL,
    event_key VARCHAR(160) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_approval_notification_event (event_key),
    KEY idx_project_approval_notification_recipient (recipient_id, created_at),
    CONSTRAINT fk_project_approval_notification_request FOREIGN KEY (request_id)
        REFERENCES project_approval_request (id),
    CONSTRAINT fk_project_approval_notification_recipient FOREIGN KEY (recipient_id)
        REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
