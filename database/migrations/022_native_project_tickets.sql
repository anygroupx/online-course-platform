-- Native, locally owner-bound text tickets. Requires migration 021. Explicit migration authorization required.
-- Never import the upstream owner/project-wide ticket list as user-owned tickets.
CREATE TABLE service_project_ticket (
 id VARCHAR(36) NOT NULL PRIMARY KEY, account_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL, provider_id BIGINT NOT NULL, provider_identity CHAR(64) NOT NULL, remote_project_id VARCHAR(19) NOT NULL,
 project_title VARCHAR(100) NOT NULL, remote_ticket_id VARCHAR(19) NULL, state VARCHAR(24) NOT NULL,
 request_encrypted MEDIUMTEXT NOT NULL, snapshot_encrypted MEDIUMTEXT NULL,
 pending_operation_id VARCHAR(36) NULL, version BIGINT NOT NULL DEFAULT 0,
 checked_at DATETIME NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_project_ticket_remote (provider_id, remote_ticket_id),
 UNIQUE KEY uk_project_ticket_source (provider_identity, remote_ticket_id),
 KEY idx_project_ticket_owner (user_id, create_time),
 CONSTRAINT fk_project_ticket_account FOREIGN KEY (account_id) REFERENCES service_project_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE service_project_ticket_operation (
 id VARCHAR(36) NOT NULL PRIMARY KEY, ticket_id VARCHAR(36) NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, state VARCHAR(24) NOT NULL, provider_version BIGINT NOT NULL, ticket_version BIGINT NOT NULL,
 payload_encrypted MEDIUMTEXT NOT NULL, resolution_evidence_encrypted TEXT NULL, resolved_by BIGINT NULL,
 expires_at DATETIME NOT NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_project_ticket_op_ticket (ticket_id, create_time),
 KEY idx_project_ticket_op_state (state, update_time),
 CONSTRAINT fk_project_ticket_operation_ticket FOREIGN KEY (ticket_id) REFERENCES service_project_ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
