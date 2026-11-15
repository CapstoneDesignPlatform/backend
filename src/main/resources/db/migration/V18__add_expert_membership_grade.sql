ALTER TABLE expert_profiles
    ADD COLUMN membership_grade VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    ADD CONSTRAINT ck_expert_profiles_membership_grade
        CHECK (membership_grade IN ('BUSINESS', 'SPECIAL', 'PREMIUM'));
