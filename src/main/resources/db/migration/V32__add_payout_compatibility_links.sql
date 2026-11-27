-- Legacy payout IDs are aliases only. FinancialOperation remains the sole new execution owner.
CREATE TABLE payout_compatibility_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    approval_id BIGINT NOT NULL,
    operation_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_payout_compat_review UNIQUE (review_id),
    CONSTRAINT uk_payout_compat_approval UNIQUE (approval_id),
    CONSTRAINT uk_payout_compat_operation UNIQUE (operation_id),
    CONSTRAINT fk_payout_compat_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payout_compat_review FOREIGN KEY (review_id) REFERENCES settlement_reviews(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payout_compat_approval FOREIGN KEY (approval_id) REFERENCES financial_approvals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payout_compat_operation FOREIGN KEY (operation_id) REFERENCES financial_operations(id) ON DELETE RESTRICT
);

-- Keep old InternalPayout IDs stable and make newly issued compatibility IDs unambiguous.
SELECT COALESCE(MAX(id), 0) + 1 INTO @next_payout_compat_id FROM internal_payouts;
SET @payout_compat_ddl = CONCAT('ALTER TABLE payout_compatibility_links AUTO_INCREMENT = ', @next_payout_compat_id);
PREPARE payout_compat_stmt FROM @payout_compat_ddl;
EXECUTE payout_compat_stmt;
DEALLOCATE PREPARE payout_compat_stmt;
