-- Native service commerce. Apply explicitly; never execute legacy supplier SQL.
-- Existing account_ledger is reused; monetary changes and operation transitions share a transaction.
CREATE TABLE IF NOT EXISTS service_product (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 provider_id BIGINT NOT NULL, provider_type VARCHAR(20) NOT NULL,
 project VARCHAR(16) NOT NULL, remote_product_id VARCHAR(64) NOT NULL,
 title VARCHAR(100) NOT NULL, description VARCHAR(1000), unit_price DECIMAL(16,6) NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_service_product_binding (provider_id, project, remote_product_id),
 CONSTRAINT fk_service_product_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS service_order (
 id VARCHAR(36) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 provider_id BIGINT NOT NULL, provider_version BIGINT NOT NULL, provider_identity CHAR(64) NOT NULL, provider_type VARCHAR(20) NOT NULL,
 project VARCHAR(16) NOT NULL, remote_product_id VARCHAR(64) NOT NULL, external_order_no VARCHAR(64), external_sub_order_no VARCHAR(64),
 title VARCHAR(100) NOT NULL, account_label VARCHAR(120) NOT NULL, status VARCHAR(24) NOT NULL,
 quantity INT NOT NULL, completed INT NOT NULL DEFAULT 0, distance DECIMAL(8,2) NOT NULL,
 unit_charge DECIMAL(16,6) NOT NULL, paid_amount DECIMAL(14,2) NOT NULL, refunded_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0, pending_operation_id VARCHAR(36),
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_service_order_owner (user_id, create_time), KEY idx_service_order_status (status, update_time),
 UNIQUE KEY uk_service_remote_order (provider_id, project, external_order_no),
 CONSTRAINT fk_service_order_product FOREIGN KEY (product_id) REFERENCES service_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS service_order_operation (
 id VARCHAR(36) NOT NULL PRIMARY KEY, order_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL, product_version BIGINT NOT NULL, provider_version BIGINT NOT NULL,
 order_version BIGINT, action VARCHAR(20) NOT NULL, state VARCHAR(20) NOT NULL,
 quantity INT NOT NULL, distance DECIMAL(8,2) NOT NULL, unit_charge DECIMAL(16,6) NOT NULL,
 amount DECIMAL(14,2) NOT NULL, account_label VARCHAR(120) NOT NULL,
 payload_encrypted MEDIUMTEXT, error_category VARCHAR(40),
 resolved_by BIGINT, resolution_note VARCHAR(1000), expires_at DATETIME NOT NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_service_operation_owner (user_id, create_time),
 KEY idx_service_operation_order (order_id, create_time),
 KEY idx_service_operation_state (state, update_time),
 CONSTRAINT fk_service_operation_product FOREIGN KEY (product_id) REFERENCES service_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
