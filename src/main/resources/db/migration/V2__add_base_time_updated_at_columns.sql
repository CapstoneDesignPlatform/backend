ALTER TABLE announcement
    ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE announcement
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE announcement
    MODIFY updated_at DATETIME(6) NOT NULL;

ALTER TABLE inquiry
    ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE inquiry
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE inquiry
    MODIFY updated_at DATETIME(6) NOT NULL;

ALTER TABLE files
    ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE files
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE files
    MODIFY updated_at DATETIME(6) NOT NULL;
