-- Native source-platform downstream customers. Not the upstream one-user/project wallet.
-- Review and explicitly apply after021. No automatic migration; disabled by native feature flag.
CREATE TABLE project_client (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 project_title VARCHAR(100) NOT NULL, label VARCHAR(100) NOT NULL, status VARCHAR(24) NOT NULL,
 version BIGINT NOT NULL, balance DECIMAL(20,6) NOT NULL, unit_price DECIMAL(16,6) NOT NULL,
 refundable_units DECIMAL(20,6) NOT NULL, refund_budget DECIMAL(14,2) NOT NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_client_owner_project (owner_id,project_id,create_time),
 CONSTRAINT ck_project_client_balances CHECK (balance>=0 AND refundable_units>=0 AND refund_budget>=0 AND unit_price>0),
 CONSTRAINT ck_project_client_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
 CONSTRAINT fk_project_client_project FOREIGN KEY (project_id) REFERENCES service_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE project_client_operation (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, request_id VARCHAR(36) NOT NULL,
 request_hash CHAR(64) NOT NULL, client_id VARCHAR(36) NOT NULL, project_id BIGINT NOT NULL,
 project_title VARCHAR(100) NOT NULL, label VARCHAR(100) NOT NULL, project_version BIGINT NOT NULL,
 client_version BIGINT NULL, action VARCHAR(20) NOT NULL, state VARCHAR(24) NOT NULL,
 units DECIMAL(20,6) NOT NULL, unit_price DECIMAL(16,6) NOT NULL, amount DECIMAL(14,2) NOT NULL,
 client_balance_after DECIMAL(20,6) NULL, wallet_balance_after DECIMAL(14,2) NULL,
 expires_at DATETIME NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_client_operation_request (owner_id,request_id),
 KEY idx_client_operation_owner (owner_id,create_time), KEY idx_client_operation_client (client_id,create_time),
 CONSTRAINT ck_project_client_operation_amount CHECK (units>=0 AND amount>=0),
 CONSTRAINT fk_client_operation_project FOREIGN KEY (project_id) REFERENCES service_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE project_api_credential (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, subject VARCHAR(36) NOT NULL,
 access_mode VARCHAR(20) NOT NULL, version BIGINT NOT NULL, key_hash CHAR(64) NULL, prefix VARCHAR(12) NULL,
 expires_at DATETIME NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_project_api_subject (owner_id,subject), UNIQUE KEY uk_project_api_hash (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE project_api_call (
 id VARCHAR(36) NOT NULL PRIMARY KEY, owner_id BIGINT NOT NULL, credential_id VARCHAR(36) NOT NULL,
 action VARCHAR(40) NOT NULL, outcome VARCHAR(12) NOT NULL, create_time DATETIME NOT NULL,
 KEY idx_project_api_call_owner (owner_id,create_time),
 CONSTRAINT fk_project_api_call_credential FOREIGN KEY (credential_id) REFERENCES project_api_credential(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
