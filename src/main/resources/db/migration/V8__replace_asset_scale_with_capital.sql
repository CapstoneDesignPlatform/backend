ALTER TABLE announcement
    DROP COLUMN asset_scale;

ALTER TABLE announcement
    ADD COLUMN capital DECIMAL(15, 2) NOT NULL COMMENT '자본금 (억원 단위)',
    ADD COLUMN capital_scale DECIMAL(15, 2) COMMENT '자본규모 (억원 단위, 창업예정이면 null)';
