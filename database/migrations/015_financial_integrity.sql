-- P0 Financial Integrity
-- Apply once after 014_p0_secret_data_transition.sql.
-- This migration is non-destructive and preserves existing card usability by hashing
-- historical plaintext before clearing it.

ALTER TABLE `recharge_card`
    ADD COLUMN `password_hash` CHAR(64) NULL COMMENT '卡密 SHA-256（高熵密钥）' AFTER `card_password`;

UPDATE `recharge_card`
SET `password_hash` = SHA2(`card_password`, 256)
WHERE (`password_hash` IS NULL OR `password_hash` = '')
  AND `card_password` IS NOT NULL
  AND `card_password` <> '';

ALTER TABLE `recharge_card`
    MODIFY COLUMN `card_password` VARCHAR(32) NULL COMMENT '历史明文字段（迁移后必须为空）',
    MODIFY COLUMN `password_hash` CHAR(64) NOT NULL COMMENT '卡密 SHA-256（高熵密钥）';

UPDATE `recharge_card` SET `card_password` = NULL WHERE `card_password` IS NOT NULL;

-- 同一支付宝事件不得绑定到不同本地订单。
ALTER TABLE `payment_event`
    ADD UNIQUE KEY `uk_payment_provider_event` (`provider_event_id`);

-- 订单删除改为逻辑归档，保留订单与账本之间的审计链路。
ALTER TABLE `course_order`
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑归档：0-否 1-是' AFTER `update_time`,
    ADD KEY `idx_course_order_deleted` (`is_deleted`);

-- 数据库级最后防线：即使未来代码回归，也不能写入负余额或非法流水。
ALTER TABLE `sys_user`
    ADD CONSTRAINT `chk_sys_user_balance_nonnegative` CHECK (`balance` >= 0);

ALTER TABLE `account_ledger`
    ADD CONSTRAINT `chk_ledger_amount_positive` CHECK (`amount` > 0),
    ADD CONSTRAINT `chk_ledger_direction` CHECK (`direction` IN (-1, 1)),
    ADD CONSTRAINT `chk_ledger_balance_nonnegative` CHECK (`balance_before` >= 0 AND `balance_after` >= 0);
