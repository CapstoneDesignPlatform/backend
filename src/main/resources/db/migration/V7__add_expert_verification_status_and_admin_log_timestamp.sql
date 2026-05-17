ALTER TABLE expert_profiles
    ADD COLUMN verification_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SUBMITTED' AFTER is_verified;

UPDATE expert_profiles
SET verification_status = CASE
    WHEN is_verified = TRUE THEN 'APPROVED'
    ELSE 'NOT_SUBMITTED'
END;

ALTER TABLE admin_logs
    MODIFY COLUMN admin_id BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at;
