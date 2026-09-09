-- Requires 018/019. Temporary account authorization; never deploy without explicit authorization.
CREATE TABLE service_account_session (
 id VARCHAR(36) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_version BIGINT NOT NULL, provider_version BIGINT NOT NULL,
 mode VARCHAR(16) NOT NULL, state VARCHAR(24) NOT NULL, account_label VARCHAR(120) NOT NULL,
 rules_refresh_at DATETIME NULL, snapshot_encrypted MEDIUMTEXT NULL, version BIGINT NOT NULL DEFAULT 0,
 expires_at DATETIME NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_service_account_owner (user_id, expires_at),
 KEY idx_service_account_expiry (expires_at),
 CONSTRAINT fk_service_account_product FOREIGN KEY (product_id) REFERENCES service_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE service_order_operation ADD COLUMN account_session_id VARCHAR(36) NULL;
ALTER TABLE service_order_operation ADD COLUMN account_session_version BIGINT NULL;
