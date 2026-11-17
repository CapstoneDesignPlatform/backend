-- Internal financial workflow completion. Existing V1-V23 are immutable.
CREATE TABLE settlement_profile_reviews (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, profile_id BIGINT NOT NULL, admin_id BIGINT NOT NULL,
 profile_revision BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 decision VARCHAR(20) NOT NULL, documents VARCHAR(20) NOT NULL, reason_code VARCHAR(100) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE(profile_id,request_key), FOREIGN KEY(profile_id) REFERENCES expert_settlement_profiles(id) ON DELETE RESTRICT,
 FOREIGN KEY(admin_id) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(profile_revision>0), CHECK(decision IN ('APPROVED','ON_HOLD','REJECTED')),
 CHECK(documents IN ('ACCEPTED','MISSING','REJECTED')), CHECK(decision<>'APPROVED' OR documents='ACCEPTED')
);
ALTER TABLE financial_tax_invoices ADD COLUMN execution_owner VARCHAR(20) NULL, ADD COLUMN provider_environment VARCHAR(30) NULL;
ALTER TABLE internal_tax_documents ADD COLUMN balance_version BIGINT NULL, ADD COLUMN profile_id BIGINT NULL,
 ADD COLUMN profile_review_id BIGINT NULL, ADD COLUMN reviewed_policy_version VARCHAR(100) NULL,
 ADD COLUMN revision_of_id BIGINT NULL, ADD COLUMN superseded BIT NOT NULL DEFAULT 0,
 ADD CONSTRAINT fk_tax_document_revision FOREIGN KEY(revision_of_id) REFERENCES internal_tax_documents(id) ON DELETE RESTRICT,
 ADD CONSTRAINT fk_tax_document_profile FOREIGN KEY(profile_id) REFERENCES expert_settlement_profiles(id) ON DELETE RESTRICT,
 ADD CONSTRAINT fk_tax_document_review FOREIGN KEY(profile_review_id) REFERENCES settlement_profile_reviews(id) ON DELETE RESTRICT;
ALTER TABLE settlement_reviews ADD COLUMN profile_review_id BIGINT NULL,
 ADD CONSTRAINT fk_settlement_profile_review FOREIGN KEY(profile_review_id) REFERENCES settlement_profile_reviews(id) ON DELETE RESTRICT;
ALTER TABLE internal_payouts ADD COLUMN lookup_count INT NOT NULL DEFAULT 0, ADD COLUMN next_lookup_at DATETIME(6) NULL,
 ADD CONSTRAINT ck_internal_payout_lookup CHECK(lookup_count>=0);
CREATE TABLE internal_tax_executions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, document_id BIGINT NOT NULL UNIQUE, invoice_id BIGINT NOT NULL UNIQUE,
 identity VARCHAR(50) NOT NULL UNIQUE, request_key VARCHAR(64) NOT NULL, snapshot_hash VARCHAR(64) NOT NULL,
 policy_version VARCHAR(100) NOT NULL, provider_code VARCHAR(50) NOT NULL, provider_environment VARCHAR(30) NOT NULL,
 document_version BIGINT NOT NULL, state VARCHAR(20) NOT NULL, nts_state VARCHAR(20) NOT NULL,
 external_id VARCHAR(200) NULL UNIQUE, dispatched BIT NOT NULL, test_issuance BIT NOT NULL,
 lease VARCHAR(50) NULL, lease_until DATETIME(6) NULL, lookup_count INT NOT NULL DEFAULT 0,
 next_lookup_at DATETIME(6) NULL, failure_code VARCHAR(50) NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(document_id) REFERENCES internal_tax_documents(id) ON DELETE RESTRICT,
 FOREIGN KEY(invoice_id) REFERENCES financial_tax_invoices(id) ON DELETE RESTRICT,
 CHECK(state IN ('READY','REQUESTED','UNKNOWN','ISSUED','FAILED')),
 CHECK(nts_state IN ('NOT_SENT','PENDING','SUCCEEDED','FAILED','UNKNOWN')), CHECK(lookup_count>=0 AND document_version>=0)
);
CREATE TABLE financial_audit_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payout_id BIGINT NULL, tax_execution_id BIGINT NULL, profile_id BIGINT NULL,
 document_id BIGINT NULL, actor_id BIGINT NULL, event_key VARCHAR(150) NOT NULL UNIQUE,
 code VARCHAR(50) NOT NULL, state_code VARCHAR(40) NOT NULL, reason_code VARCHAR(100) NULL, snapshot_hash VARCHAR(64) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(payout_id) REFERENCES internal_payouts(id) ON DELETE RESTRICT,
 FOREIGN KEY(tax_execution_id) REFERENCES internal_tax_executions(id) ON DELETE RESTRICT,
 FOREIGN KEY(profile_id) REFERENCES expert_settlement_profiles(id) ON DELETE RESTRICT,
 FOREIGN KEY(document_id) REFERENCES internal_tax_documents(id) ON DELETE RESTRICT,
 FOREIGN KEY(actor_id) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK((payout_id IS NOT NULL)+(tax_execution_id IS NOT NULL)+(profile_id IS NOT NULL)+(document_id IS NOT NULL)=1)
);
CREATE INDEX idx_internal_payout_recovery ON internal_payouts(dispatched,state,next_lookup_at);
CREATE INDEX idx_internal_tax_recovery ON internal_tax_executions(dispatched,state,next_lookup_at);
