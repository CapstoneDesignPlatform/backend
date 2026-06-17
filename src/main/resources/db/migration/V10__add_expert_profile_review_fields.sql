ALTER TABLE expert_profiles
    ADD COLUMN rejected_reason VARCHAR(500) NULL AFTER verified_at,
    ADD COLUMN reviewed_at DATETIME(6) NULL AFTER rejected_reason;

UPDATE expert_profiles
SET reviewed_at = verified_at
WHERE verification_status = 'APPROVED'
  AND verified_at IS NOT NULL;
