CREATE TABLE IF NOT EXISTS admin_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    admin_role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_accounts_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS admin_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NULL,
    description TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_user_id BIGINT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NULL,
    reason VARCHAR(100) NOT NULL,
    content TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_by BIGINT NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_reports_status (status)
);

CREATE TABLE IF NOT EXISTS user_sanctions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_user_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    sanction_type VARCHAR(30) NOT NULL,
    reason TEXT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receiver_user_id BIGINT NOT NULL,
    sender_admin_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_receiver_user_id (receiver_user_id)
);
