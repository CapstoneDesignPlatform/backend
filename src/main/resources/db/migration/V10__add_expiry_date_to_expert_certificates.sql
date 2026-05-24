ALTER TABLE expert_certificates
    ADD COLUMN expiry_date DATE NULL AFTER issue_date;