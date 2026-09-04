-- 013 P0: normalized RBAC and server-side permission backfill
-- Apply after 007_security_hardening.sql. This migration is additive and preserves existing users.

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_code` VARCHAR(64) NOT NULL,
  `role_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC roles';

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `permission_code` VARCHAR(128) NOT NULL,
  `permission_name` VARCHAR(128) NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RBAC permissions';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_role` (`user_id`, `role_id`),
  KEY `idx_sys_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User to role assignments';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_permission` (`role_id`, `permission_id`),
  KEY `idx_sys_role_permission_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Role to permission grants';

INSERT INTO `sys_role` (`role_code`, `role_name`) VALUES
('SUPER_ADMIN', '超级管理员'),
('OPERATOR', '运营'),
('FINANCE', '财务'),
('CUSTOMER_SERVICE', '客服'),
('USER', '普通用户'),
('AUDITOR', '审计员')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `enabled` = 1;

INSERT INTO `sys_permission` (`permission_code`, `permission_name`) VALUES
('user:read', '读取用户'), ('user:update', '更新用户'),
('order:read', '读取订单'), ('order:update', '更新订单'),
('payment:read', '读取支付'), ('payment:refund', '支付退款'), ('payment:config', '支付配置'), ('payment:reconcile', '执行支付对账'),
('announcement:create', '创建公告'), ('announcement:update', '更新公告'),
('announcement:delete', '删除公告'), ('announcement:publish', '发布/下线公告'),
('customer-service:read', '读取客服会话'), ('customer-service:assign', '分配客服会话'),
('customer-service:take', '接管客服会话'), ('customer-service:read:any', '读取任意客服会话'),
('api-provider:read', '读取API供应商'), ('api-provider:update', '更新API供应商'),
('security:event:read', '读取安全事件'),
('system-config:read', '读取系统配置'), ('system-config:update', '更新系统配置'),
('platform:read', '读取平台配置'), ('platform:update', '更新平台配置'),
('mfa:manage', '管理MFA'), ('rbac:manage', '管理RBAC角色')
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`), `enabled` = 1;

-- SUPER_ADMIN receives every permission.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.role_code = 'SUPER_ADMIN';

-- Least-privilege staff grants.
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','user:update','order:read','order:update','announcement:create','announcement:update',
 'announcement:delete','announcement:publish','api-provider:read','api-provider:update',
 'system-config:read','system-config:update','platform:read','platform:update')
WHERE r.role_code = 'OPERATOR';

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','order:read','payment:read','payment:refund','payment:config','payment:reconcile')
WHERE r.role_code = 'FINANCE';

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','order:read','customer-service:read','customer-service:take')
WHERE r.role_code = 'CUSTOMER_SERVICE';

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
('user:read','order:read','payment:read','api-provider:read','security:event:read',
 'system-config:read','platform:read')
WHERE r.role_code = 'AUDITOR';

-- Preserve historical role assignments without trusting an account id.
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = CASE UPPER(COALESCE(u.role, 'USER'))
  WHEN 'ADMIN' THEN 'SUPER_ADMIN'
  WHEN 'SUPER_ADMIN' THEN 'SUPER_ADMIN'
  WHEN 'OPERATOR' THEN 'OPERATOR'
  WHEN 'FINANCE' THEN 'FINANCE'
  WHEN 'CS' THEN 'CUSTOMER_SERVICE'
  WHEN 'CUSTOMER_SERVICE' THEN 'CUSTOMER_SERVICE'
  WHEN 'AUDITOR' THEN 'AUDITOR'
  ELSE 'USER'
END;
