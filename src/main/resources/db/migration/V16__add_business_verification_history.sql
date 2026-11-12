ALTER TABLE expert_business_registration_infos
    ADD COLUMN input_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN confirmed_input_revision BIGINT NULL,
    ADD COLUMN confirmed_file_id BIGINT NULL,
    ADD COLUMN confirmed_ocr_revision BIGINT NULL;

CREATE TABLE expert_business_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_registration_info_id BIGINT NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    execution_status VARCHAR(20) NOT NULL,
    verification_kind VARCHAR(20) NOT NULL,
    authenticity VARCHAR(30) NOT NULL,
    business_state VARCHAR(30) NOT NULL,
    failure VARCHAR(40) NOT NULL,
    validity VARCHAR(10) NOT NULL,
    file_id BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    ocr_revision BIGINT NOT NULL,
    input_revision BIGINT NOT NULL,
    status_lookup_performed BOOLEAN NOT NULL DEFAULT FALSE,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_business_verifications_execution UNIQUE (execution_id),
    CONSTRAINT fk_business_verifications_info FOREIGN KEY (business_registration_info_id)
        REFERENCES expert_business_registration_infos(id) ON DELETE RESTRICT,
    KEY idx_business_verifications_latest (business_registration_info_id, id),
    KEY idx_business_verifications_running (business_registration_info_id, execution_status)
);
-- File references above are snapshots, deliberately without an FK to files.
-- Existing business rows have no confirmation proof; never backfill an invented confirmation.
