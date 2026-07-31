-- 009 Refresh Token 哈希存储收尾迁移
-- 先回填历史令牌哈希，再放宽旧明文字段约束，最后清除明文。

UPDATE `refresh_token`
SET `token_hash` = LOWER(SHA2(`token`, 256))
WHERE `token` IS NOT NULL
  AND `token` <> ''
  AND (`token_hash` IS NULL OR `token_hash` = '');

ALTER TABLE `refresh_token`
  MODIFY COLUMN `token` VARCHAR(500) NULL COMMENT 'Refresh Token 明文（已停用）';

UPDATE `refresh_token`
SET `token` = NULL
WHERE `token_hash` IS NOT NULL
  AND `token_hash` <> '';
