-- Explicit existing-only course price/description refresh. Apply only with deployment approval.
-- No course/category creation, no supplier writes, no balance changes.
CREATE TABLE course_price_refresh (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL, provider_id BIGINT NOT NULL, provider_version BIGINT NOT NULL,
 state VARCHAR(24) NOT NULL, plan_encrypted MEDIUMTEXT NOT NULL,
 expires_at DATETIME NOT NULL, applied_at DATETIME NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_course_refresh_owner (user_id,create_time),
 CONSTRAINT fk_course_refresh_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
