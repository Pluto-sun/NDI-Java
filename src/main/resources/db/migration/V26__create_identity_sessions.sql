ALTER TABLE identity_user_account
    ADD COLUMN email VARCHAR(254) NULL,
    ADD COLUMN password_hash VARCHAR(255) NULL,
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_identity_user_email (email);

CREATE TABLE identity_user_session (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_session_token (token_hash),
    KEY idx_identity_session_user (user_id, revoked_at),
    KEY idx_identity_session_expiry (expires_at),
    CONSTRAINT fk_identity_session_user FOREIGN KEY (user_id) REFERENCES identity_user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 初始化管理员及后续账号治理共用此行，序列化跨实例的管理操作。
CREATE TABLE identity_administration_lock (
    id TINYINT NOT NULL PRIMARY KEY,
    CONSTRAINT chk_identity_administration_lock CHECK (id = 1)
) ENGINE = InnoDB;
INSERT INTO identity_administration_lock (id) VALUES (1);
