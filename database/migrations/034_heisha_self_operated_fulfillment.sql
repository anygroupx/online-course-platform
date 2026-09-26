-- Black Shark self-operated fulfillment. Apply explicitly before deploying schema-dependent code.
-- Existing products and orders remain upstream-routed historical records.
ALTER TABLE service_product
  ADD COLUMN fulfillment_mode VARCHAR(20) NOT NULL DEFAULT 'UPSTREAM' AFTER enabled;
ALTER TABLE service_product
  ADD CONSTRAINT chk_service_product_fulfillment
  CHECK (fulfillment_mode IN ('UPSTREAM', 'SELF_OPERATED'));
CREATE INDEX idx_service_product_fulfillment
  ON service_product (provider_type, fulfillment_mode, enabled);

ALTER TABLE service_order
  ADD COLUMN fulfillment_mode VARCHAR(20) NOT NULL DEFAULT 'UPSTREAM' AFTER status;
ALTER TABLE service_order
  ADD CONSTRAINT chk_service_order_fulfillment
  CHECK (fulfillment_mode IN ('UPSTREAM', 'SELF_OPERATED'));
CREATE INDEX idx_service_order_fulfillment
  ON service_order (fulfillment_mode, status, update_time);

ALTER TABLE service_order_operation
  ADD COLUMN previous_status VARCHAR(24) NULL AFTER resolution_note;
ALTER TABLE service_order_operation
  ADD COLUMN resulting_status VARCHAR(24) NULL AFTER previous_status;
ALTER TABLE service_order_operation
  ADD COLUMN completed_snapshot INT NULL AFTER resulting_status;
ALTER TABLE service_order_operation
  ADD CONSTRAINT chk_service_operation_completed_snapshot
  CHECK (completed_snapshot IS NULL OR completed_snapshot >= 0);

CREATE TABLE service_order_fulfillment (
  order_id VARCHAR(36) NOT NULL PRIMARY KEY,
  payload_encrypted MEDIUMTEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  CONSTRAINT fk_service_fulfillment_order
    FOREIGN KEY (order_id) REFERENCES service_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('service-order:fulfill', '处理自营服务订单')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'service-order:fulfill'
WHERE r.role_code IN ('SUPER_ADMIN', 'OPERATOR');
