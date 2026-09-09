-- Native opt-in ShowDoc notifications. Review and explicitly apply only after migration018.
-- No PHP notification/binding pseudo-endpoints are executed. No credentials appear in views/logs.
CREATE TABLE service_notification_preference (
 order_id VARCHAR(36) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL,
 token_encrypted TEXT NULL, enabled BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL,
 code_hash CHAR(64) NULL, challenge_delivery_id VARCHAR(36) NULL, challenge_expires_at DATETIME NULL,
 verify_attempts INT NOT NULL DEFAULT 0, verified_at DATETIME NULL, observed_json TEXT NULL,
 sequence BIGINT NOT NULL DEFAULT 0, scanned_at DATETIME NOT NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 KEY idx_notification_preference_scan (enabled,scanned_at),
 CONSTRAINT fk_notification_preference_order FOREIGN KEY (order_id) REFERENCES service_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE service_notification_delivery (
 id VARCHAR(36) NOT NULL PRIMARY KEY, order_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 preference_version BIGINT NOT NULL, sequence BIGINT NOT NULL, kind VARCHAR(24) NOT NULL,
 state VARCHAR(24) NOT NULL, payload_encrypted TEXT NULL, expires_at DATETIME NOT NULL,
 create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
 UNIQUE KEY uk_notification_event (order_id,preference_version,sequence),
 KEY idx_notification_ready (state,update_time,id), KEY idx_notification_expiry (state,expires_at),
 KEY idx_notification_history (order_id,user_id,create_time,id),
 CONSTRAINT fk_notification_delivery_preference FOREIGN KEY (order_id) REFERENCES service_notification_preference(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
