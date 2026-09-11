ALTER TABLE client_info
    MODIFY COLUMN representative_name VARCHAR(50) NULL,
    MODIFY COLUMN email VARCHAR(100) NULL,
    ADD COLUMN manager_name VARCHAR(50) NULL AFTER representative_name;
