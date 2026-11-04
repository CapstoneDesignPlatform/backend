ALTER TABLE announcement
    ADD COLUMN progress_step VARCHAR(50) NULL AFTER status;

UPDATE announcement a
SET a.progress_step = CASE
    WHEN a.status = 'ACTIVE' THEN 'STEP_2_BID_CLOSED'
    WHEN a.status = 'CLOSED' AND EXISTS (
        SELECT 1
        FROM bids b
        WHERE b.announcement_code = a.announcement_code
          AND b.status = 'SELECTED'
    ) THEN 'STEP_4_DIAGNOSIS_STARTED'
    WHEN a.status = 'CLOSED' THEN 'STEP_3_EXPERT_SELECTION'
    ELSE 'STEP_1_REGISTERED'
END;

ALTER TABLE announcement
    MODIFY COLUMN progress_step VARCHAR(50) NOT NULL DEFAULT 'STEP_1_REGISTERED';

ALTER TABLE bids
    ADD COLUMN final_amount DECIMAL(15, 2) NULL AFTER bid_amount;
