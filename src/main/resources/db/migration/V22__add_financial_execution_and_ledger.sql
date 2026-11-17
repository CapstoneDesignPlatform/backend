-- Durable finance commands and encrypted snapshots. No existing migration is changed.
CREATE TABLE expert_settlement_accounts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, expert_profile_id BIGINT NOT NULL, account_revision BIGINT NOT NULL,
 masked_number VARCHAR(40) NOT NULL, encrypted_details LONGTEXT NOT NULL, state VARCHAR(20) NOT NULL,
 kyc_verified BIT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_expert_account_revision UNIQUE(expert_profile_id,account_revision),
 CONSTRAINT fk_account_expert FOREIGN KEY(expert_profile_id) REFERENCES expert_profiles(id) ON DELETE RESTRICT,
 CONSTRAINT ck_account_revision CHECK(account_revision>0)
);
CREATE TABLE settlement_balances (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL UNIQUE,
 gross DECIMAL(15,2) NOT NULL, platform_rate DECIMAL(5,4) NOT NULL, policy_version VARCHAR(50) NOT NULL,
 refunded DECIMAL(15,2) NOT NULL, reserved DECIMAL(15,2) NOT NULL, paid DECIMAL(15,2) NOT NULL,
 recoverable DECIMAL(15,2) NOT NULL, held BIT NOT NULL, review_sequence BIGINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_balance_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT ck_balance_amount CHECK(gross>0 AND refunded>=0 AND refunded<=gross AND reserved>=0 AND paid>=0 AND recoverable>=0),
 CONSTRAINT ck_balance_rate CHECK(platform_rate>=0 AND platform_rate<=1)
);
CREATE TABLE financial_approvals (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, admin_user_id BIGINT NOT NULL, payment_id BIGINT NOT NULL,
 refund_request_id BIGINT NULL, account_id BIGINT NULL, balance_version BIGINT NOT NULL, account_revision BIGINT NULL,
 amount DECIMAL(15,2) NOT NULL, kind VARCHAR(20) NOT NULL, decision VARCHAR(20) NOT NULL,
 reason_code VARCHAR(100) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_approval_actor FOREIGN KEY(admin_user_id) REFERENCES users(id) ON DELETE RESTRICT,
 CONSTRAINT fk_approval_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT fk_approval_refund FOREIGN KEY(refund_request_id) REFERENCES payment_refund_requests(id) ON DELETE RESTRICT,
 CONSTRAINT fk_approval_account FOREIGN KEY(account_id) REFERENCES expert_settlement_accounts(id) ON DELETE RESTRICT,
 CONSTRAINT ck_approval_amount CHECK(amount>0 AND amount=FLOOR(amount)),
 CONSTRAINT ck_approval_kind CHECK((kind='REFUND' AND refund_request_id IS NOT NULL AND account_id IS NULL)
    OR (kind='PAYOUT' AND refund_request_id IS NULL AND (decision<>'APPROVED' OR account_id IS NOT NULL)))
);
CREATE TABLE billing_party_snapshots (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, created_by BIGINT NOT NULL, subject_user_id BIGINT NULL,
 role VARCHAR(20) NOT NULL, encrypted_details LONGTEXT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_party_creator FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE RESTRICT,
 CONSTRAINT fk_party_subject FOREIGN KEY(subject_user_id) REFERENCES users(id) ON DELETE RESTRICT,
 CONSTRAINT ck_party_role CHECK((role='PLATFORM' AND subject_user_id IS NULL) OR (role IN ('CLIENT','EXPERT') AND subject_user_id IS NOT NULL))
);
CREATE TABLE financial_tax_invoices (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, draft_id BIGINT NOT NULL UNIQUE, issuance_scope_key VARCHAR(64) NOT NULL UNIQUE, payment_id BIGINT NOT NULL,
 supplier_snapshot_id BIGINT NOT NULL, recipient_snapshot_id BIGINT NOT NULL, original_invoice_id BIGINT NULL,
 modification VARCHAR(50) NULL, taxation VARCHAR(20) NOT NULL, write_date DATE NOT NULL,
 supply DECIMAL(15,2) NOT NULL, vat DECIMAL(15,2) NOT NULL, total DECIMAL(15,2) NOT NULL,
 issued BIT NOT NULL, external_id VARCHAR(200) NULL UNIQUE, nts_state VARCHAR(20) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_invoice_draft FOREIGN KEY(draft_id) REFERENCES payment_tax_invoice_drafts(id) ON DELETE RESTRICT,
 CONSTRAINT fk_invoice_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT fk_invoice_supplier FOREIGN KEY(supplier_snapshot_id) REFERENCES billing_party_snapshots(id) ON DELETE RESTRICT,
 CONSTRAINT fk_invoice_recipient FOREIGN KEY(recipient_snapshot_id) REFERENCES billing_party_snapshots(id) ON DELETE RESTRICT,
 CONSTRAINT fk_invoice_original FOREIGN KEY(original_invoice_id) REFERENCES financial_tax_invoices(id) ON DELETE RESTRICT,
 CONSTRAINT ck_invoice_total CHECK(total=supply+vat AND total<>0 AND supply=FLOOR(supply) AND vat=FLOOR(vat)),
 CONSTRAINT ck_invoice_modification CHECK((original_invoice_id IS NULL AND modification IS NULL) OR (original_invoice_id IS NOT NULL AND modification IS NOT NULL))
);
CREATE TABLE financial_operations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, reference VARCHAR(40) NOT NULL UNIQUE, kind VARCHAR(20) NOT NULL,
 provider_code VARCHAR(40) NOT NULL, provider_merchant_id VARCHAR(100) NOT NULL,
 provider_store_id VARCHAR(100) NOT NULL, provider_environment VARCHAR(20) NOT NULL,
 payment_id BIGINT NULL, refund_request_id BIGINT NULL UNIQUE, seller_draft_id BIGINT NULL,
 account_id BIGINT NULL, approval_id BIGINT NULL UNIQUE, invoice_id BIGINT NULL UNIQUE,
 source_version BIGINT NOT NULL, key_hash VARCHAR(64) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 encrypted_payload LONGTEXT NOT NULL, external_id VARCHAR(200) NULL,
 unique_external_id VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN kind='PARTNER' THEN NULL ELSE external_id END) STORED,
 state VARCHAR(20) NOT NULL, nts_state VARCHAR(20) NOT NULL,
 execution_token VARCHAR(36) NULL, lease_until DATETIME(6) NULL,
 next_lookup_at DATETIME(6) NOT NULL, submit_claimed BIT NOT NULL DEFAULT 0, lookup_count INT NOT NULL DEFAULT 0,
 failure_code VARCHAR(60) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_financial_request UNIQUE(kind,key_hash),
 CONSTRAINT uk_financial_partner_version UNIQUE(seller_draft_id,account_id,source_version),
 CONSTRAINT uk_financial_external UNIQUE(provider_code,provider_merchant_id,provider_store_id,provider_environment,kind,unique_external_id),
 CONSTRAINT fk_operation_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT fk_operation_refund FOREIGN KEY(refund_request_id) REFERENCES payment_refund_requests(id) ON DELETE RESTRICT,
 CONSTRAINT fk_operation_seller FOREIGN KEY(seller_draft_id) REFERENCES expert_seller_drafts(id) ON DELETE RESTRICT,
 CONSTRAINT fk_operation_account FOREIGN KEY(account_id) REFERENCES expert_settlement_accounts(id) ON DELETE RESTRICT,
 CONSTRAINT fk_operation_approval FOREIGN KEY(approval_id) REFERENCES financial_approvals(id) ON DELETE RESTRICT,
 CONSTRAINT fk_operation_invoice FOREIGN KEY(invoice_id) REFERENCES financial_tax_invoices(id) ON DELETE RESTRICT,
 CONSTRAINT ck_operation_kind CHECK(
  (kind='REFUND' AND payment_id IS NOT NULL AND refund_request_id IS NOT NULL AND approval_id IS NOT NULL AND seller_draft_id IS NULL AND account_id IS NULL AND invoice_id IS NULL)
  OR (kind='PARTNER' AND payment_id IS NULL AND seller_draft_id IS NOT NULL AND account_id IS NOT NULL AND refund_request_id IS NULL AND approval_id IS NULL AND invoice_id IS NULL)
  OR (kind='PAYOUT' AND payment_id IS NOT NULL AND approval_id IS NOT NULL AND account_id IS NOT NULL AND refund_request_id IS NULL AND seller_draft_id IS NULL AND invoice_id IS NULL)
  OR (kind='TAX_INVOICE' AND payment_id IS NOT NULL AND invoice_id IS NOT NULL AND refund_request_id IS NULL AND seller_draft_id IS NULL AND account_id IS NULL AND approval_id IS NULL)),
 INDEX ix_financial_due(state,next_lookup_at)
);
CREATE TABLE financial_ledger_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL, operation_id BIGINT NULL,
 movement_key VARCHAR(100) NOT NULL, bucket VARCHAR(30) NOT NULL, signed_amount DECIMAL(15,2) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_ledger_movement_bucket UNIQUE(movement_key,bucket),
 CONSTRAINT fk_ledger_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT fk_ledger_operation FOREIGN KEY(operation_id) REFERENCES financial_operations(id) ON DELETE RESTRICT
);
CREATE TABLE settlement_adjustments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL, operation_id BIGINT NOT NULL UNIQUE,
 refund_amount DECIMAL(15,2) NOT NULL, net_before DECIMAL(15,2) NOT NULL, net_after DECIMAL(15,2) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_adjustment_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT fk_adjustment_operation FOREIGN KEY(operation_id) REFERENCES financial_operations(id) ON DELETE RESTRICT,
 CONSTRAINT ck_adjustment_amount CHECK(refund_amount>0 AND net_after=net_before-refund_amount AND net_after>=0)
);
CREATE TABLE financial_operation_audits (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, operation_id BIGINT NOT NULL, actor_id BIGINT NULL,
 action VARCHAR(40) NOT NULL, state_code VARCHAR(60) NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_fin_audit_operation FOREIGN KEY(operation_id) REFERENCES financial_operations(id) ON DELETE RESTRICT,
 CONSTRAINT fk_fin_audit_actor FOREIGN KEY(actor_id) REFERENCES users(id) ON DELETE RESTRICT
);
ALTER TABLE payment_reconciliation_jobs ADD COLUMN worker_token VARCHAR(36) NULL,
 ADD COLUMN worker_lease_until DATETIME(6) NULL, ADD COLUMN lookup_count INT NOT NULL DEFAULT 0;

CREATE TABLE payment_lookup_audits (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, job_id BIGINT NOT NULL, attempt_id BIGINT NOT NULL,
 result_code VARCHAR(40) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_lookup_audit_job FOREIGN KEY(job_id) REFERENCES payment_reconciliation_jobs(id) ON DELETE RESTRICT,
 CONSTRAINT fk_lookup_audit_attempt FOREIGN KEY(attempt_id) REFERENCES payment_attempts(id) ON DELETE RESTRICT
);
