-- Requires migration 018. Explicitly authorize and validate against the target MySQL before applying.
-- sxdk_tw has no verified live-price endpoint. Administrators must attest a time-limited contract price.
ALTER TABLE service_product ADD COLUMN contract_unit_cost DECIMAL(16,6) NULL;
ALTER TABLE service_product ADD COLUMN contract_valid_until DATE NULL;
ALTER TABLE service_product ADD COLUMN contract_evidence VARCHAR(1000) NULL;
ALTER TABLE service_product ADD COLUMN contract_reviewed_by BIGINT NULL;
ALTER TABLE service_product ADD COLUMN contract_reviewed_at DATETIME NULL;
ALTER TABLE service_product ADD COLUMN contract_provider_identity CHAR(64) NULL;
-- Calendar snapshots contain only scheduling/paid-date data, never student credentials or addresses.
ALTER TABLE service_order ADD COLUMN schedule_json MEDIUMTEXT NULL;
ALTER TABLE service_order_operation ADD COLUMN schedule_json MEDIUMTEXT NULL;
ALTER TABLE service_order MODIFY COLUMN distance DECIMAL(8,2) NULL;
ALTER TABLE service_order_operation MODIFY COLUMN distance DECIMAL(8,2) NULL;
