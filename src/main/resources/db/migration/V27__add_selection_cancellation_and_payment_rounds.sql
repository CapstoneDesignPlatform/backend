ALTER TABLE announcement ADD COLUMN selection_round BIGINT NOT NULL DEFAULT 0;
ALTER TABLE bids ADD COLUMN rejected_selection_round BIGINT NULL;
ALTER TABLE payments ADD COLUMN selection_round BIGINT NOT NULL DEFAULT 1;

-- Existing selections/orders belong to the first round. No progress or payment status is changed.
UPDATE announcement a SET a.selection_round = 1
WHERE EXISTS (SELECT 1 FROM bids b WHERE b.announcement_code = a.announcement_code AND b.status = 'SELECTED')
   OR EXISTS (SELECT 1 FROM payments p WHERE p.announcement_id = a.id);

UPDATE bids b JOIN announcement a ON a.announcement_code = b.announcement_code
SET b.rejected_selection_round = a.selection_round
WHERE b.status = 'REJECTED' AND a.selection_round > 0;

ALTER TABLE payments ADD CONSTRAINT uk_payments_bid_round UNIQUE (bid_id, selection_round);
ALTER TABLE payments DROP INDEX uk_payments_bid;
ALTER TABLE payments ADD CONSTRAINT ck_payments_selection_round CHECK (selection_round > 0);
ALTER TABLE announcement ADD CONSTRAINT ck_announcement_selection_round CHECK (selection_round >= 0);

CREATE TABLE bid_selection_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    announcement_id BIGINT NOT NULL,
    bid_id BIGINT NOT NULL,
    selection_round BIGINT NOT NULL,
    expert_profile_id BIGINT NOT NULL,
    final_amount DECIMAL(15,2) NULL,
    selected_by BIGINT NULL,
    selected_at DATETIME(6) NOT NULL,
    cancelled_by BIGINT NULL,
    cancelled_at DATETIME(6) NULL,
    cancel_reason VARCHAR(500) NULL,
    CONSTRAINT uk_selection_history_round UNIQUE (announcement_id, selection_round),
    CONSTRAINT fk_selection_history_announcement FOREIGN KEY (announcement_id) REFERENCES announcement(id),
    CONSTRAINT fk_selection_history_bid FOREIGN KEY (bid_id) REFERENCES bids(id),
    CONSTRAINT fk_selection_history_expert FOREIGN KEY (expert_profile_id) REFERENCES expert_profiles(id),
    CONSTRAINT fk_selection_history_selected_by FOREIGN KEY (selected_by) REFERENCES users(id),
    CONSTRAINT fk_selection_history_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users(id)
);

INSERT INTO bid_selection_history
    (announcement_id, bid_id, selection_round, expert_profile_id, final_amount, selected_by, selected_at)
SELECT a.id, b.id, a.selection_round, e.id, b.final_amount, a.user_id,
       COALESCE(b.updated_at, b.submitted_at, CURRENT_TIMESTAMP(6))
FROM announcement a JOIN bids b ON b.announcement_code = a.announcement_code AND b.status = 'SELECTED'
JOIN expert_profiles e ON e.user_id = b.expert_user_id;
