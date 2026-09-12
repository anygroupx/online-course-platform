-- Course identifiers are opaque values returned by course lookup providers.
-- Widen only: existing identifiers and order state remain unchanged.
-- Review and authorization required before applying to an existing database.
ALTER TABLE course_order MODIFY COLUMN course_id VARCHAR(2048) NULL COMMENT '课程ID';
