ALTER TABLE payments
    ADD COLUMN active_attempt_id BIGINT NULL,
    ADD COLUMN paid_at DATETIME(6) NULL;

ALTER TABLE payment_attempts
    ADD COLUMN method VARCHAR(30) NULL,
    ADD COLUMN charge_amount DECIMAL(15,2) NULL,
    ADD COLUMN currency VARCHAR(3) NULL,
    ADD COLUMN order_id VARCHAR(40) NULL,
    ADD COLUMN request_key_hash VARCHAR(64) NULL,
    ADD COLUMN confirmation_hash VARCHAR(64) NULL,
    ADD COLUMN provider_code VARCHAR(40) NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN merchant_id VARCHAR(100) NULL,
    ADD COLUMN environment VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN provider_transaction_id VARCHAR(200) NULL,
    ADD COLUMN provider_raw_status VARCHAR(80) NULL,
    ADD COLUMN failure VARCHAR(40) NOT NULL DEFAULT 'NONE',
    ADD COLUMN execution_token VARCHAR(36) NULL,
    ADD COLUMN lease_until DATETIME(6) NULL;

UPDATE payment_attempts a JOIN payments p ON p.id=a.payment_id
SET a.method=p.method,a.charge_amount=p.amount,a.currency=p.currency,a.order_id=p.order_id;

ALTER TABLE payment_attempts
    MODIFY COLUMN method VARCHAR(30) NOT NULL,
    MODIFY COLUMN charge_amount DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN currency VARCHAR(3) NOT NULL,
    MODIFY COLUMN order_id VARCHAR(40) NOT NULL,
    ADD COLUMN active_payment_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('PREPARED','REQUESTED','WAITING_DEPOSIT','UNKNOWN','SUCCEEDED') THEN payment_id ELSE NULL END) STORED,
    ADD CONSTRAINT uk_payment_active_attempt UNIQUE(active_payment_id),
    ADD CONSTRAINT uk_payment_attempt_request UNIQUE(payment_id,request_key_hash),
    ADD CONSTRAINT uk_payment_provider_transaction UNIQUE(provider_code,merchant_id,environment,provider_transaction_id),
    ADD CONSTRAINT ck_payment_attempt_charge CHECK(charge_amount>0 AND charge_amount=FLOOR(charge_amount) AND currency='KRW'),
    ADD CONSTRAINT ck_payment_provider_identity CHECK(provider_transaction_id IS NULL OR
        (merchant_id IS NOT NULL AND merchant_id <> '' AND provider_code <> 'DISABLED' AND environment <> 'DISABLED'));

ALTER TABLE payments ADD CONSTRAINT fk_payment_active_attempt
    FOREIGN KEY(active_attempt_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT;

CREATE TABLE payment_reconciliation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    due_at DATETIME(6) NOT NULL,
    failure VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_job_attempt UNIQUE(attempt_id),
    CONSTRAINT fk_payment_job_attempt FOREIGN KEY(attempt_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT,
    INDEX idx_payment_jobs_due(state,due_at)
);
CREATE TABLE payment_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    provider_code VARCHAR(40) NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    event_key_hash VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    processing_token VARCHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_event_identity UNIQUE(provider_code,merchant_id,environment,event_key_hash),
    CONSTRAINT fk_payment_event_attempt FOREIGN KEY(attempt_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT
);
-- No charge is replayed by this migration. Ambiguous legacy active attempts cause UNIQUE validation to fail closed.
