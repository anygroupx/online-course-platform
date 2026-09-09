-- Native project/customer subwallets. Requires account_ledger and saved api_provider configuration.
-- Review/backup and explicit authorization required; not applied to production by application startup.
CREATE TABLE service_project (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, provider_id BIGINT NOT NULL,
 remote_project_id VARCHAR(19) NOT NULL, title VARCHAR(100) NOT NULL, description VARCHAR(1000),
 base_price DECIMAL(16,6) NOT NULL, unit_price DECIMAL(16,6) NOT NULL, unit_cost DECIMAL(16,6) NOT NULL,
 valid_until DATE NOT NULL, price_evidence VARCHAR(1000) NOT NULL, reviewed_by BIGINT NOT NULL,
 reviewed_at DATETIME NOT NULL, provider_identity CHAR(64) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 0, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_service_project_binding (provider_id, remote_project_id),
 CONSTRAINT fk_service_project_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE service_project_account (
 id VARCHAR(36) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 provider_id BIGINT NOT NULL, provider_identity CHAR(64) NOT NULL, remote_project_id VARCHAR(19) NOT NULL,
 remote_customer_id VARCHAR(19) NULL, customer_key_encrypted TEXT NULL, state VARCHAR(24) NOT NULL,
 unit_price DECIMAL(16,6) NOT NULL, remote_balance DECIMAL(20,6) NULL,
 refundable_units DECIMAL(20,6) NOT NULL DEFAULT 0, refund_budget DECIMAL(14,2) NOT NULL DEFAULT 0,
 balance_checked_at DATETIME NULL, pending_operation_id VARCHAR(36) NULL, version BIGINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_service_project_owner (user_id, project_id),
 UNIQUE KEY uk_service_project_remote_customer (provider_id, remote_customer_id),
 UNIQUE KEY uk_service_project_source_customer (provider_identity, remote_customer_id),
 CONSTRAINT fk_service_project_account_project FOREIGN KEY (project_id) REFERENCES service_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE service_project_operation (
 id VARCHAR(36) NOT NULL PRIMARY KEY, account_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL, project_title VARCHAR(100) NOT NULL, project_version BIGINT NOT NULL,
 provider_version BIGINT NOT NULL, account_version BIGINT NULL, action VARCHAR(20) NOT NULL,
 state VARCHAR(24) NOT NULL, units DECIMAL(20,6) NOT NULL, unit_price DECIMAL(16,6) NOT NULL,
 unit_cost DECIMAL(16,6) NOT NULL, amount DECIMAL(14,2) NOT NULL, balance_after DECIMAL(20,6) NULL,
 error_category VARCHAR(40) NULL, resolved_by BIGINT NULL, resolution_evidence VARCHAR(1000) NULL,
 expires_at DATETIME NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_project_operation_owner (user_id, create_time),
 KEY idx_project_operation_account (account_id, create_time),
 KEY idx_project_operation_state (state, update_time),
 CONSTRAINT fk_service_project_operation_project FOREIGN KEY (project_id) REFERENCES service_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
