-- Providerless Black Shark self-operated checkout and qualification workflow.
-- Apply explicitly after 034 and before deploying schema-dependent code.
ALTER TABLE service_product DROP FOREIGN KEY fk_service_product_provider;
ALTER TABLE service_product MODIFY COLUMN provider_id BIGINT NULL;
ALTER TABLE service_product
  ADD COLUMN self_operated_provider_type VARCHAR(20)
    GENERATED ALWAYS AS (CASE WHEN fulfillment_mode = 'SELF_OPERATED' THEN provider_type ELSE NULL END) STORED;
-- Check duplicate legacy local SKUs before changing any persisted binding. If this fails,
-- stop and explicitly reconcile duplicate products; never merge or delete order history here.
ALTER TABLE service_product
  ADD UNIQUE KEY uk_service_product_self_operated
    (self_operated_provider_type, project, remote_product_id, fulfillment_mode);
-- 034 allowed a provider-bound local product. Detach only local products, and invalidate
-- their existing quotes by advancing the product version. Upstream snapshots remain intact.
UPDATE service_product SET provider_id = NULL, version = version + 1
WHERE fulfillment_mode = 'SELF_OPERATED';
ALTER TABLE service_product
  ADD CONSTRAINT fk_service_product_provider FOREIGN KEY (provider_id) REFERENCES api_provider(id),
  ADD CONSTRAINT chk_service_product_provider_mode CHECK (
    (fulfillment_mode = 'UPSTREAM' AND provider_id IS NOT NULL) OR
    (fulfillment_mode = 'SELF_OPERATED' AND provider_id IS NULL AND provider_type = 'heisha'
      AND project = 'default' AND remote_product_id IN ('1','2','3','4'))
  );

ALTER TABLE service_order MODIFY COLUMN provider_id BIGINT NULL;
ALTER TABLE service_order MODIFY COLUMN provider_version BIGINT NULL;
ALTER TABLE service_order MODIFY COLUMN provider_identity CHAR(64) NULL;
-- Use the immutable order mode, not its product's current mode.
UPDATE service_order SET provider_id = NULL, provider_version = NULL, provider_identity = NULL
WHERE fulfillment_mode = 'SELF_OPERATED';
ALTER TABLE service_order ADD CONSTRAINT chk_service_order_provider_mode CHECK (
  (fulfillment_mode = 'UPSTREAM' AND provider_id IS NOT NULL AND provider_version IS NOT NULL AND provider_identity IS NOT NULL) OR
  (fulfillment_mode = 'SELF_OPERATED' AND provider_id IS NULL AND provider_version IS NULL
    AND provider_identity IS NULL AND provider_type = 'heisha')
);

ALTER TABLE service_order_operation MODIFY COLUMN provider_version BIGINT NULL;
UPDATE service_order_operation SET provider_version = NULL
WHERE order_id IN (SELECT id FROM service_order WHERE fulfillment_mode = 'SELF_OPERATED');
ALTER TABLE service_order_operation MODIFY COLUMN action VARCHAR(32) NOT NULL;
ALTER TABLE service_order_operation
  ADD COLUMN previous_verification_status VARCHAR(20) NULL AFTER completed_snapshot,
  ADD COLUMN resulting_verification_status VARCHAR(20) NULL AFTER previous_verification_status,
  ADD CONSTRAINT chk_service_operation_verification CHECK (
    (previous_verification_status IS NULL OR previous_verification_status IN ('PENDING','VERIFIED','NEEDS_INFO','REJECTED')) AND
    (resulting_verification_status IS NULL OR resulting_verification_status IN ('PENDING','VERIFIED','NEEDS_INFO','REJECTED'))
  );

ALTER TABLE service_order_fulfillment
  ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER payload_encrypted,
  ADD COLUMN verified_by BIGINT NULL AFTER verification_status,
  ADD COLUMN verified_at DATETIME NULL AFTER verified_by,
  ADD COLUMN verification_note VARCHAR(1000) NULL AFTER verified_at,
  ADD COLUMN material_version BIGINT NOT NULL DEFAULT 0 AFTER verification_note,
  ADD CONSTRAINT chk_service_fulfillment_verification CHECK (
    verification_status IN ('PENDING','VERIFIED','NEEDS_INFO','REJECTED'));
CREATE INDEX idx_service_fulfillment_verification
  ON service_order_fulfillment (verification_status, update_time);

CREATE TABLE service_fulfillment_material_draft (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_version BIGINT NOT NULL,
  asset_type VARCHAR(24) NOT NULL,
  content_encrypted MEDIUMTEXT NULL,
  mime_type VARCHAR(40) NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  byte_size INT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  state VARCHAR(12) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  purged_at DATETIME NULL,
  CONSTRAINT fk_service_material_draft_product FOREIGN KEY (product_id) REFERENCES service_product(id),
  CONSTRAINT chk_service_material_draft_state CHECK (state IN ('READY','USED','EXPIRED')),
  CONSTRAINT chk_service_material_draft_asset CHECK (asset_type = 'FACE_QUALIFICATION'),
  CONSTRAINT chk_service_material_draft_dimensions CHECK (width > 0 AND height > 0 AND byte_size > 0),
  KEY idx_service_material_draft_expiry (state, expires_at),
  KEY idx_service_material_draft_owner (user_id, product_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE service_order_fulfillment_asset (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  order_id VARCHAR(36) NOT NULL,
  asset_type VARCHAR(24) NOT NULL,
  content_encrypted MEDIUMTEXT NULL,
  mime_type VARCHAR(40) NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  byte_size INT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  purged_at DATETIME NULL,
  CONSTRAINT fk_service_fulfillment_asset_order FOREIGN KEY (order_id) REFERENCES service_order(id),
  CONSTRAINT chk_service_fulfillment_asset_type CHECK (asset_type = 'FACE_QUALIFICATION'),
  CONSTRAINT chk_service_fulfillment_asset_dimensions CHECK (width > 0 AND height > 0 AND byte_size > 0),
  UNIQUE KEY uk_service_fulfillment_asset_order_type (order_id, asset_type),
  KEY idx_service_fulfillment_asset_purge (purged_at, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission (permission_code, permission_name)
VALUES ('service-order:biometric', '查看订单敏感资格材料')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1;
DELETE FROM sys_role_permission
WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE permission_code = 'service-order:biometric'
) AND role_id NOT IN (
  SELECT id FROM sys_role WHERE role_code IN ('SUPER_ADMIN', 'OPERATOR')
);
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p
  ON p.permission_code = 'service-order:biometric'
WHERE r.role_code IN ('SUPER_ADMIN', 'OPERATOR');
