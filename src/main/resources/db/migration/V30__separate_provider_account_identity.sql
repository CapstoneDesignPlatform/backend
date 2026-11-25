-- Preserve existing external merchant_id values. Internal account partitions get explicit, namespaced keys.
ALTER TABLE payment_attempts
    ADD COLUMN provider_account_id VARCHAR(200) NULL;

UPDATE payment_attempts
SET provider_account_id = CONCAT('provider:', provider_code, ':legacy-merchant:', merchant_id)
WHERE merchant_id IS NOT NULL;

ALTER TABLE payment_attempts
    DROP CHECK ck_payment_provider_identity,
    DROP INDEX uk_payment_provider_transaction,
    ADD CONSTRAINT uk_payment_provider_transaction_account
        UNIQUE (provider_code, provider_account_id, environment, provider_transaction_id),
    ADD CONSTRAINT ck_payment_provider_identity CHECK (provider_transaction_id IS NULL OR
        (merchant_id IS NOT NULL AND merchant_id <> '' AND provider_account_id IS NOT NULL
         AND provider_code <> 'DISABLED' AND environment <> 'DISABLED'));

ALTER TABLE payment_webhook_events
    ADD COLUMN provider_account_id VARCHAR(200) NULL;

-- Reuse the attempt's already-backfilled identity. This keeps old inbox rows
-- deduplicated under the exact legacy identity used when that attempt is resumed.
UPDATE payment_webhook_events e
JOIN payment_attempts a ON a.id = e.attempt_id
SET e.provider_account_id = a.provider_account_id;

ALTER TABLE payment_webhook_events
    MODIFY COLUMN merchant_id VARCHAR(100) NULL,
    MODIFY COLUMN provider_account_id VARCHAR(200) NOT NULL,
    DROP INDEX uk_payment_event_identity,
    ADD CONSTRAINT uk_payment_event_account_identity
        UNIQUE (provider_code, provider_account_id, environment, event_key_hash);

ALTER TABLE financial_operations
    ADD COLUMN provider_account_id VARCHAR(200) NULL;

UPDATE financial_operations
SET provider_account_id = CONCAT('provider:', provider_code, ':store:', provider_store_id);

ALTER TABLE financial_operations
    MODIFY COLUMN provider_merchant_id VARCHAR(100) NULL,
    MODIFY COLUMN provider_account_id VARCHAR(200) NOT NULL,
    DROP INDEX uk_financial_external,
    ADD CONSTRAINT uk_financial_external_account
        UNIQUE (provider_code, provider_account_id, provider_environment, kind, unique_external_id);
