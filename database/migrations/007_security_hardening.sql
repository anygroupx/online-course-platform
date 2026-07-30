-- 007 安全加固：RBAC、Token哈希、资金流水、强制改密、API Key哈希
-- 执行前请备份数据库。若列已存在请跳过对应 ALTER。

-- 用户表增强
ALTER TABLE `sys_user`
  ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/CS/USER' AFTER `status`,
  ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码：0-否 1-是' AFTER `role`,
  ADD COLUMN `password_changed_at` DATETIME DEFAULT NULL COMMENT '密码最后修改时间' AFTER `must_change_password`,
  ADD COLUMN `api_key_hash` VARCHAR(128) DEFAULT NULL COMMENT 'API Key哈希' AFTER `api_key`,
  ADD COLUMN `api_key_prefix` VARCHAR(32) DEFAULT NULL COMMENT 'API Key前缀(仅展示)' AFTER `api_key_hash`,
  ADD COLUMN `api_key_scopes` VARCHAR(255) DEFAULT NULL COMMENT 'API Key作用域,逗号分隔' AFTER `api_key_prefix`,
  ADD COLUMN `api_key_expire_time` DATETIME DEFAULT NULL COMMENT 'API Key过期时间' AFTER `api_key_scopes`;

UPDATE `sys_user` SET `role` = 'ADMIN', `must_change_password` = 1 WHERE `id` = 1;

-- Refresh Token 表增强
ALTER TABLE `refresh_token`
  ADD COLUMN `token_hash` VARCHAR(128) DEFAULT NULL COMMENT 'Token SHA-256 哈希' AFTER `token`,
  ADD COLUMN `token_family_id` VARCHAR(64) DEFAULT NULL COMMENT 'Token家族ID' AFTER `token_hash`,
  ADD COLUMN `revoked_at` DATETIME DEFAULT NULL COMMENT '撤销时间' AFTER `expire_time`,
  ADD COLUMN `replaced_by` VARCHAR(128) DEFAULT NULL COMMENT '被替换的token哈希' AFTER `revoked_at`,
  ADD COLUMN `last_used_ip` VARCHAR(64) DEFAULT NULL COMMENT '最后使用IP' AFTER `replaced_by`;

ALTER TABLE `refresh_token` ADD INDEX `idx_refresh_token_hash` (`token_hash`);
ALTER TABLE `refresh_token` ADD INDEX `idx_refresh_token_user` (`user_id`);

-- 资金流水（不可变账本）
CREATE TABLE IF NOT EXISTS `account_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型：PAYMENT/ORDER/RECHARGE/REFUND/API_FEE/ADJUST',
  `biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号',
  `direction` TINYINT NOT NULL COMMENT '1-入账 -1-出账',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '变动金额(正数)',
  `balance_before` DECIMAL(12,2) NOT NULL COMMENT '变动前余额',
  `balance_after` DECIMAL(12,2) NOT NULL COMMENT '变动后余额',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_biz` (`biz_type`, `biz_no`, `direction`, `user_id`),
  KEY `idx_ledger_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水账本';

-- 支付事件幂等表
CREATE TABLE IF NOT EXISTS `payment_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(64) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL COMMENT 'PAID/REFUND/CLOSE',
  `provider_event_id` VARCHAR(128) DEFAULT NULL COMMENT '第三方事件ID',
  `payload` TEXT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_event` (`order_no`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付事件幂等表';


-- 强制默认管理员首次登录改密（若仍使用历史弱口令）
UPDATE `sys_user` SET `must_change_password` = 1
WHERE `username` = 'admin' AND (`password_changed_at` IS NULL);
