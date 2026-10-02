-- Current RBAC policy is static-role-policy; role CRUD/policy editing is not exposed.
-- Apply after 035 with explicit deployment authorization.
INSERT INTO sys_permission (permission_code, permission_name) VALUES
('service-product:read', '查看服务商品'),
('service-product:update', '管理服务商品'),
('service-order:read', '查看服务订单'),
('service-order:fulfill', '处理服务订单'),
('service-order:biometric', '查看订单敏感资格材料'),
('service-order:reconcile', '核对服务订单'),
('service-order:refund', '服务订单退款'),
('service-project:read', '查看服务项目'),
('service-project:update', '管理服务项目')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), enabled = 1;

-- Reset only this domain's fixed-role grants. Preserve unrelated permissions.
DELETE FROM sys_role_permission WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE permission_code IN (
'service-product:read', 'service-product:update', 'service-order:read', 'service-order:fulfill', 'service-order:biometric', 'service-order:reconcile', 'service-order:refund', 'service-project:read', 'service-project:update'));

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'OPERATOR' AND p.permission_code IN ('service-product:read', 'service-product:update', 'service-order:read', 'service-order:fulfill', 'service-order:biometric', 'service-project:read', 'service-project:update');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'FINANCE' AND p.permission_code IN ('service-order:read', 'service-order:refund', 'service-order:reconcile');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'AUDITOR' AND p.permission_code IN ('service-product:read', 'service-order:read', 'service-project:read');
