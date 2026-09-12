-- Read-only status checks. Apply explicitly before deploying this schema-dependent code.
-- Existing status, money, contracts and business versions are not rewritten.
ALTER TABLE service_order ADD COLUMN status_checked_at DATETIME NULL;
ALTER TABLE service_order ADD COLUMN status_check_after DATETIME NULL;
ALTER TABLE service_order ADD COLUMN status_check_attempt_at DATETIME NULL;
ALTER TABLE service_order ADD COLUMN status_check_failures INT NOT NULL DEFAULT 0;
ALTER TABLE service_order ADD COLUMN status_check_state VARCHAR(8) NULL;
ALTER TABLE service_order ADD COLUMN status_check_token VARCHAR(36) NULL;
ALTER TABLE service_order ADD COLUMN status_check_until DATETIME NULL;
ALTER TABLE service_order ADD CONSTRAINT chk_service_status_failures CHECK (status_check_failures BETWEEN 0 AND 8);
ALTER TABLE service_order ADD CONSTRAINT chk_service_status_check_state CHECK (status_check_state IS NULL OR status_check_state IN ('OK', 'RETRY'));
ALTER TABLE service_order ADD CONSTRAINT chk_service_status_check_lease CHECK ((status_check_token IS NULL AND status_check_until IS NULL) OR (status_check_token IS NOT NULL AND status_check_until IS NOT NULL));
CREATE INDEX idx_service_order_check ON service_order (provider_type, status, status_check_after, status_check_until);
