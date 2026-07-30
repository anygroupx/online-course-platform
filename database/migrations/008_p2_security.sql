-- 008 P2 安全建设：MFA、安全审计、支付对账
-- 执行前请备份。若列/表已存在请跳过对应语句。

ALTER TABLE `sys_user`
  ADD COLUMN `mfa_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用MFA：0否1是' AFTER `must_change_password`,
  ADD COLUMN `mfa_secret` VARCHAR(128) DEFAULT NULL COMMENT 'TOTP密钥(加密存储)' AFTER `mfa_enabled`,
  ADD COLUMN `mfa_enabled_at` DATETIME DEFAULT NULL COMMENT 'MFA启用时间' AFTER `mfa_secret`,
  ADD COLUMN `mfa_backup_codes_hash` VARCHAR(512) DEFAULT NULL COMMENT '备用恢复码哈希,逗号分隔' AFTER `mfa_enabled_at`;

CREATE TABLE IF NOT EXISTS `security_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(64) NOT NULL COMMENT 'LOGIN_FAIL/ACCESS_DENIED/PAYMENT_CALLBACK/KEY_CHANGE/MFA/RECONCILE/ALERT',
  `severity` VARCHAR(16) NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/CRITICAL',
  `user_id` BIGINT DEFAULT NULL,
  `username` VARCHAR(64) DEFAULT NULL,
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `request_path` VARCHAR(255) DEFAULT NULL,
  `http_method` VARCHAR(16) DEFAULT NULL,
  `message` VARCHAR(512) NOT NULL,
  `detail` TEXT,
  `trace_id` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_security_audit_type_time` (`event_type`, `create_time`),
  KEY `idx_security_audit_user` (`user_id`),
  KEY `idx_security_audit_severity` (`severity`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集中式安全审计日志';

CREATE TABLE IF NOT EXISTS `payment_reconcile_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `biz_date` DATE NOT NULL COMMENT '对账业务日',
  `status` VARCHAR(32) NOT NULL COMMENT 'MATCHED/MISMATCH/ERROR',
  `paid_order_count` INT NOT NULL DEFAULT 0,
  `paid_order_amount` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `ledger_credit_count` INT NOT NULL DEFAULT 0,
  `ledger_credit_amount` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `missing_ledger_count` INT NOT NULL DEFAULT 0,
  `extra_ledger_count` INT NOT NULL DEFAULT 0,
  `amount_diff` DECIMAL(14,2) NOT NULL DEFAULT 0,
  `detail_json` MEDIUMTEXT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reconcile_biz_date` (`biz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付日终对账报告';

CREATE TABLE IF NOT EXISTS `mfa_challenge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `challenge_id` VARCHAR(64) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `expire_time` DATETIME NOT NULL,
  `consumed` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mfa_challenge` (`challenge_id`),
  KEY `idx_mfa_challenge_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MFA登录挑战';
