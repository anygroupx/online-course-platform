-- Explicit order-receipt reconciliation; no payments or supplier writes.
-- Review and authorization required. Application startup never applies this DDL.
CREATE TABLE course_order_receipt_recovery (
 id VARCHAR(36) NOT NULL PRIMARY KEY, actor_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 owner_id BIGINT NOT NULL, provider_id BIGINT NOT NULL,
 order_no VARCHAR(50) NOT NULL, course_name VARCHAR(255) NOT NULL, receipt_id VARCHAR(50) NOT NULL,
 state VARCHAR(20) NOT NULL, request_hash CHAR(64) NOT NULL, order_hash CHAR(64) NOT NULL,
 platform_hash CHAR(64) NOT NULL, provider_hash CHAR(64) NOT NULL, source_identity CHAR(64) NOT NULL,
 evidence_encrypted MEDIUMTEXT NOT NULL, expires_at DATETIME NOT NULL,
 applied_at DATETIME NULL, create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_receipt_recovery_actor (actor_id,order_id,create_time),
 CONSTRAINT ck_receipt_recovery_state CHECK (state IN ('READING','READY','APPLIED','CONFLICT','EXPIRED','READ_FAILED','INTERRUPTED')),
 CONSTRAINT fk_receipt_recovery_order FOREIGN KEY (order_id) REFERENCES course_order(id),
 CONSTRAINT fk_receipt_recovery_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE course_order_receipt_claim (
 source_identity CHAR(64) NOT NULL, receipt_id VARCHAR(50) NOT NULL,
 order_id BIGINT NOT NULL, recovery_id VARCHAR(36) NOT NULL,
 create_time DATETIME NOT NULL,
 PRIMARY KEY (source_identity,receipt_id), UNIQUE KEY uk_receipt_claim_order (order_id),
 CONSTRAINT fk_receipt_claim_order FOREIGN KEY (order_id) REFERENCES course_order(id),
 CONSTRAINT fk_receipt_claim_recovery FOREIGN KEY (recovery_id) REFERENCES course_order_receipt_recovery(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
