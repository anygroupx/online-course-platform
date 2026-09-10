ALTER TABLE course_platform
    ADD COLUMN display_name VARCHAR(100) NULL AFTER name;

ALTER TABLE platform_category
    ADD COLUMN price_multiplier DECIMAL(5,2) NULL DEFAULT NULL AFTER name;
