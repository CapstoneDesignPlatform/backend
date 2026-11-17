CREATE TABLE internal_payouts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, identity VARCHAR(50) NOT NULL UNIQUE,
 payment_id BIGINT NOT NULL, review_id BIGINT NOT NULL UNIQUE, profile_id BIGINT NOT NULL, balance_version BIGINT NOT NULL,
 amount DECIMAL(15,2) NOT NULL, state VARCHAR(20) NOT NULL, external_id VARCHAR(200) NULL UNIQUE,
 provider_code VARCHAR(50) NOT NULL, provider_environment VARCHAR(30) NOT NULL,
 dispatched BIT NOT NULL, lease VARCHAR(50) NULL, lease_until DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
 FOREIGN KEY(review_id) REFERENCES settlement_reviews(id) ON DELETE RESTRICT,
 FOREIGN KEY(profile_id) REFERENCES expert_settlement_profiles(id) ON DELETE RESTRICT,
 CHECK(amount>0 AND amount=FLOOR(amount)), CHECK(state IN ('RESERVED','REQUESTED','UNKNOWN','SUCCEEDED','FAILED','CANCELED'))
);
