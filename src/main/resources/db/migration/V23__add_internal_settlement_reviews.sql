CREATE TABLE expert_settlement_profiles (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, expert_profile_id BIGINT NOT NULL, revision BIGINT NOT NULL,
 business_type VARCHAR(30) NOT NULL, token_reference VARCHAR(100) NOT NULL,
 business_info_id BIGINT NOT NULL, input_revision BIGINT NOT NULL, ocr_revision BIGINT NOT NULL, file_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE(expert_profile_id,revision),
 FOREIGN KEY(expert_profile_id) REFERENCES expert_profiles(id) ON DELETE RESTRICT,
 FOREIGN KEY(business_info_id) REFERENCES expert_business_registration_infos(id) ON DELETE RESTRICT,
 FOREIGN KEY(file_id) REFERENCES files(id) ON DELETE RESTRICT,
 CHECK(revision>0 AND input_revision>=0 AND ocr_revision>0)
);
CREATE TABLE settlement_reviews (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL,
 balance_version BIGINT NOT NULL, profile_revision BIGINT NOT NULL, amount DECIMAL(15,2) NOT NULL,
 requester_id BIGINT NOT NULL, reviewer_id BIGINT NULL, state VARCHAR(30) NOT NULL, reason_code VARCHAR(100) NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 UNIQUE(payment_id,request_key),
 FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 FOREIGN KEY(requester_id) REFERENCES users(id) ON DELETE RESTRICT,
 FOREIGN KEY(reviewer_id) REFERENCES users(id) ON DELETE RESTRICT,
 CHECK(amount>0 AND amount=FLOOR(amount) AND balance_version>=0 AND profile_revision>0),
 CHECK(state IN ('PENDING_APPROVAL','APPROVED','ON_HOLD','REJECTED'))
);
