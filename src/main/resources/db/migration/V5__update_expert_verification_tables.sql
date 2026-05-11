ALTER TABLE expert_certificates
    ADD COLUMN file_id BIGINT NOT NULL AFTER expert_profile_id,
    ADD COLUMN owner_name VARCHAR(100) NOT NULL AFTER file_id,
    ADD COLUMN issue_date DATE NOT NULL AFTER certificate_number,
    DROP COLUMN registration_period,
    MODIFY COLUMN certificate_number VARCHAR(100) NOT NULL,
    MODIFY COLUMN certificate_type_code VARCHAR(30) NOT NULL,
    ADD KEY idx_expert_certificates_file_id (file_id),
    ADD CONSTRAINT fk_expert_certificates_file
        FOREIGN KEY (file_id) REFERENCES files (id);

UPDATE files
SET mime_type = 'PDF'
WHERE LOWER(mime_type) = 'application/pdf'
   OR LOWER(original_name) LIKE '%.pdf';

UPDATE files
SET mime_type = 'JPG'
WHERE LOWER(mime_type) IN ('image/jpeg', 'image/jpg')
   OR LOWER(original_name) LIKE '%.jpg'
   OR LOWER(original_name) LIKE '%.jpeg';

UPDATE files
SET mime_type = 'PNG'
WHERE LOWER(mime_type) = 'image/png'
   OR LOWER(original_name) LIKE '%.png';

ALTER TABLE files
    MODIFY COLUMN mime_type VARCHAR(30) NOT NULL;

CREATE TABLE IF NOT EXISTS expert_business_registration_infos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_profile_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    business_number VARCHAR(30) NOT NULL,
    representative_name VARCHAR(100) NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_expert_business_registration_infos_expert_profile_id (expert_profile_id),
    KEY idx_expert_business_registration_infos_file_id (file_id),
    CONSTRAINT fk_expert_business_registration_infos_expert_profile
        FOREIGN KEY (expert_profile_id) REFERENCES expert_profiles (id),
    CONSTRAINT fk_expert_business_registration_infos_file
        FOREIGN KEY (file_id) REFERENCES files (id)
);
