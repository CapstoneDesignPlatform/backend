ALTER TABLE files
    ADD COLUMN ocr_status VARCHAR(20) NOT NULL DEFAULT 'NOT_RUN',
    ADD COLUMN ocr_run_token VARCHAR(36) NULL,
    ADD COLUMN ocr_started_at DATETIME(6) NULL,
    ADD COLUMN ocr_finished_at DATETIME(6) NULL,
    ADD COLUMN ocr_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ocr_error_code VARCHAR(50) NULL;

-- Existing OCR text and legacy verification statuses are preserved, but are not
-- promoted to a verified current OCR attempt without reprocessing.
