ALTER TABLE admin_accounts
    ADD COLUMN login_id VARCHAR(100) NOT NULL AFTER user_id,
    ADD COLUMN password VARCHAR(255) NOT NULL AFTER login_id,
    ADD COLUMN last_login_at DATETIME(6) NULL AFTER status,
    ADD UNIQUE KEY uk_admin_accounts_login_id (login_id);