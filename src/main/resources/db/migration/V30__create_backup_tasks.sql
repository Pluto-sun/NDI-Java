CREATE TABLE system_backup_task (
    id BINARY(16) NOT NULL,
    requested_by BINARY(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_slot TINYINT NULL,
    requested_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    worker_token BINARY(16) NULL,
    archive_bytes BIGINT NULL,
    sha256_hex CHAR(64) NULL,
    snapshot_at DATETIME(6) NULL,
    table_count INT NULL,
    object_count INT NULL,
    error_code VARCHAR(50) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_backup_active_slot (active_slot),
    KEY idx_backup_requested (requested_at DESC,id DESC),
    CONSTRAINT fk_backup_requester FOREIGN KEY (requested_by) REFERENCES identity_user_account(id),
    CONSTRAINT chk_backup_status CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT chk_backup_slot CHECK (
        (status IN ('PENDING','RUNNING') AND active_slot IS NOT NULL AND active_slot = 1)
        OR (status IN ('SUCCEEDED','FAILED') AND active_slot IS NULL)
    ),
    CONSTRAINT chk_backup_running CHECK (status <> 'RUNNING' OR (worker_token IS NOT NULL AND started_at IS NOT NULL)),
    CONSTRAINT chk_backup_success CHECK (status <> 'SUCCEEDED' OR (
        archive_bytes IS NOT NULL AND archive_bytes > 0 AND sha256_hex IS NOT NULL AND snapshot_at IS NOT NULL
        AND table_count IS NOT NULL AND object_count IS NOT NULL
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
