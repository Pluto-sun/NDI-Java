-- 活动修订独立于主档审批版本，追加式进展只更新活动状态。
CREATE TABLE project_activity_state (
    project_id BINARY(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    last_activity_at DATETIME(6) NOT NULL,
    PRIMARY KEY (project_id),
    KEY idx_project_activity_scan (last_activity_at, project_id),
    CONSTRAINT fk_project_activity_state_project FOREIGN KEY (project_id) REFERENCES project_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO project_activity_state (project_id, revision, last_activity_at)
SELECT p.id, 0, GREATEST(p.updated_at, COALESCE(progress.latest_at, p.updated_at))
FROM project_project p
LEFT JOIN (SELECT project_id, MAX(occurred_at) AS latest_at FROM project_progress GROUP BY project_id) progress
    ON progress.project_id = p.id;

CREATE TABLE project_inactivity_case (
    id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    activity_revision BIGINT NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    province_sent_at DATETIME(6) NULL,
    interface_sent_at DATETIME(6) NULL,
    group_sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    closed_by BINARY(16) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_inactivity_cycle (project_id, activity_revision),
    KEY idx_project_inactivity_status (status, updated_at, id),
    CONSTRAINT fk_project_inactivity_project FOREIGN KEY (project_id) REFERENCES project_project (id),
    CONSTRAINT fk_project_inactivity_closer FOREIGN KEY (closed_by) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_inactivity_notice (
    id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    recipient_id BINARY(16) NOT NULL,
    kind VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    handled_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_inactivity_notice (case_id, recipient_id, kind),
    KEY idx_project_inactivity_recipient (recipient_id, handled_at, case_id),
    CONSTRAINT fk_project_inactivity_notice_case FOREIGN KEY (case_id) REFERENCES project_inactivity_case (id),
    CONSTRAINT fk_project_inactivity_notice_recipient FOREIGN KEY (recipient_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project_inactivity_dispatch (
    id BINARY(16) NOT NULL,
    case_id BINARY(16) NOT NULL,
    sender_id BINARY(16) NOT NULL,
    recipient_id BINARY(16) NOT NULL,
    sender_name VARCHAR(100) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    route VARCHAR(40) NOT NULL,
    note VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_project_inactivity_dispatch_case (case_id, created_at, id),
    CONSTRAINT fk_project_inactivity_dispatch_case FOREIGN KEY (case_id) REFERENCES project_inactivity_case (id),
    CONSTRAINT fk_project_inactivity_dispatch_sender FOREIGN KEY (sender_id) REFERENCES identity_user_account (id),
    CONSTRAINT fk_project_inactivity_dispatch_recipient FOREIGN KEY (recipient_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
