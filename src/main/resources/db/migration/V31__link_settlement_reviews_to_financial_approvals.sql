-- Link newly bridged legacy settlement reviews to the canonical approval.
-- Historical rows stay NULL unless an unambiguous, verified mapping is made by application code.
ALTER TABLE financial_approvals
    ADD COLUMN settlement_review_id BIGINT NULL,
    ADD COLUMN approval_source VARCHAR(30) NOT NULL DEFAULT 'FINANCIAL_WORKFLOW',
    ADD CONSTRAINT uk_financial_approval_settlement_review UNIQUE (settlement_review_id),
    ADD CONSTRAINT fk_financial_approval_settlement_review
        FOREIGN KEY (settlement_review_id) REFERENCES settlement_reviews(id) ON DELETE RESTRICT;
