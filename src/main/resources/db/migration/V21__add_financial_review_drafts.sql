-- Local review drafts only. No row represents a provider registration, payout or tax issuance.
CREATE TABLE payment_refund_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL, request_key_hash VARCHAR(64) NOT NULL,
 amount DECIMAL(15,2) NOT NULL, state VARCHAR(30) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_refund_payment_key UNIQUE(payment_id,request_key_hash),
 CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT ck_refund_amount CHECK(amount>0 AND amount=FLOOR(amount))
);
CREATE TABLE expert_seller_drafts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, expert_profile_id BIGINT NOT NULL, business_info_id BIGINT NOT NULL,
 input_revision BIGINT NOT NULL, file_id BIGINT NOT NULL, ocr_revision BIGINT NOT NULL,
 state VARCHAR(30) NOT NULL, version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_seller_draft_expert UNIQUE(expert_profile_id),
 CONSTRAINT fk_seller_expert FOREIGN KEY(expert_profile_id) REFERENCES expert_profiles(id) ON DELETE RESTRICT,
 CONSTRAINT fk_seller_business FOREIGN KEY(business_info_id) REFERENCES expert_business_registration_infos(id) ON DELETE RESTRICT,
 CONSTRAINT fk_seller_file FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE RESTRICT,
 CONSTRAINT ck_seller_revisions CHECK(input_revision>=0 AND ocr_revision>0)
);
CREATE TABLE payment_settlement_drafts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL, expert_profile_id BIGINT NOT NULL,
 gross DECIMAL(15,2) NOT NULL, platform_amount DECIMAL(15,2) NOT NULL, expert_amount DECIMAL(15,2) NOT NULL,
 policy_version VARCHAR(50) NOT NULL, state VARCHAR(30) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_settlement_draft_payment UNIQUE(payment_id),
 CONSTRAINT fk_settlement_draft_payment FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 CONSTRAINT fk_settlement_draft_expert FOREIGN KEY(expert_profile_id) REFERENCES expert_profiles(id) ON DELETE RESTRICT,
 CONSTRAINT ck_settlement_draft_amounts CHECK(gross>0 AND platform_amount>=0 AND expert_amount>=0 AND gross=platform_amount+expert_amount)
);
CREATE TABLE payment_tax_invoice_drafts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, settlement_id BIGINT NOT NULL, request_key_hash VARCHAR(64) NOT NULL,
 original_draft_id BIGINT NULL, kind VARCHAR(30) NOT NULL, state VARCHAR(30) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_tax_draft_request UNIQUE(settlement_id,request_key_hash),
 CONSTRAINT fk_tax_draft_settlement FOREIGN KEY(settlement_id) REFERENCES payment_settlement_drafts(id) ON DELETE RESTRICT,
 CONSTRAINT fk_tax_draft_original FOREIGN KEY(original_draft_id) REFERENCES payment_tax_invoice_drafts(id) ON DELETE RESTRICT
);
