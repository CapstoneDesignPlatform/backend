CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_type VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password VARCHAR(255) NULL,
    social_provider VARCHAR(20) NULL,
    social_id VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_social_provider_social_id UNIQUE (social_provider, social_id)
);

CREATE TABLE IF NOT EXISTS client_info (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    representative_name VARCHAR(50) NOT NULL,
    contact VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    business_number VARCHAR(30) NOT NULL,
    address VARCHAR(255) NOT NULL,
    website VARCHAR(255) NULL,
    capital DECIMAL(15, 2) NULL,
    founded_date DATE NULL,
    description TEXT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_info_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS expert_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    specialty VARCHAR(100) NOT NULL,
    business_name VARCHAR(100) NULL,
    experience_years INT NOT NULL,
    portfolio_description TEXT NULL,
    is_verified BIT NOT NULL DEFAULT 0,
    selected_count INT NOT NULL DEFAULT 0,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expert_profiles_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    client_info_id BIGINT NOT NULL,
    announcement_code VARCHAR(50) NOT NULL,
    industry VARCHAR(100) NOT NULL,
    purpose VARCHAR(100) NOT NULL,
    business_owner_type VARCHAR(50) NULL,
    category VARCHAR(50) NOT NULL,
    current_industry VARCHAR(100) NULL,
    current_license VARCHAR(100) NULL,
    job_type VARCHAR(30) NULL,
    required_license TEXT NULL,
    asset_scale DECIMAL(15, 2) NULL,
    deadline DATETIME(6) NOT NULL,
    diagnosis_reason VARCHAR(50) NOT NULL,
    diagnosis_reason_detail TEXT NULL,
    status VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_announcement_code (announcement_code)
);

CREATE TABLE IF NOT EXISTS job_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    announcement_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    license_type VARCHAR(100) NULL,
    work_type VARCHAR(100) NULL,
    client_type VARCHAR(50) NULL,
    budget DECIMAL(15, 2) NOT NULL,
    region VARCHAR(100) NULL,
    description TEXT NULL,
    bid_deadline DATETIME(6) NULL,
    status VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS bids (
    id BIGINT NOT NULL AUTO_INCREMENT,
    announcement_code VARCHAR(50) NOT NULL,
    expert_user_id BIGINT NOT NULL,
    bid_amount DECIMAL(15, 2) NOT NULL,
    bid_start_date DATE NOT NULL,
    status VARCHAR(20) NULL,
    submitted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS inquiry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    inquiry_type VARCHAR(50) NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    answer TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    answered_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS expert_certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_profile_id BIGINT NOT NULL,
    certificate_name VARCHAR(100) NOT NULL,
    certificate_number VARCHAR(100) NULL,
    registration_period VARCHAR(100) NULL,
    expired_at DATE NULL,
    certificate_type_code VARCHAR(30) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_profile_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_type VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS notification_subscription (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expert_user_id BIGINT NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    subscribed_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_subscription_expert_user_id (expert_user_id)
);
