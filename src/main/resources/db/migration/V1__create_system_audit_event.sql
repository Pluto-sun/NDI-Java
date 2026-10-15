CREATE TABLE system_audit_event (
    id BINARY(16) NOT NULL,
    actor_id BINARY(16) NULL,
    event_type VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id BINARY(16) NULL,
    detail JSON NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_system_audit_event_occurred_at (occurred_at),
    INDEX idx_system_audit_event_resource (resource_type, resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
