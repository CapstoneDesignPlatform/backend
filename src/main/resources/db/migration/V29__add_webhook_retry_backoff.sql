ALTER TABLE payment_webhook_events
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN last_failure VARCHAR(40) NULL,
    ADD COLUMN dead_letter BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_webhook_attempt_count CHECK (attempt_count >= 0),
    ADD INDEX idx_webhook_due(state, dead_letter, next_attempt_at, lease_until, id);
