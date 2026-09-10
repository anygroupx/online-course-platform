-- Native service quotes and orders retain exact six-decimal price * two-decimal distance.
-- Apply after 018/019/020 before enabling native services. Requires deployment authorization.
-- Widen only: existing six-decimal snapshots and all paid/refunded/quoted amounts stay unchanged.
-- No price backfill or repricing of historical orders/READY operations.
ALTER TABLE service_order MODIFY COLUMN unit_charge DECIMAL(18,8) NOT NULL;
ALTER TABLE service_order_operation MODIFY COLUMN unit_charge DECIMAL(18,8) NOT NULL;
