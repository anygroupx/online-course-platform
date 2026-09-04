-- P0 Refresh Session Security
-- Security cut-over: old JWT refresh tokens/localStorage sessions are revoked. Users must sign in again.

UPDATE `refresh_token`
SET `token_family_id` = LEFT(SHA2(CONCAT('family:', `id`, ':', COALESCE(`token_hash`, 'missing')), 256), 32)
WHERE `token_family_id` IS NULL OR `token_family_id` = '';

ALTER TABLE `refresh_token`
    ADD COLUMN `issued_at` DATETIME NULL COMMENT '签发时间' AFTER `token_family_id`,
    ADD COLUMN `device_info` VARCHAR(255) NULL COMMENT '设备/User-Agent摘要' AFTER `last_used_ip`,
    ADD COLUMN `revocation_reason` VARCHAR(64) NULL COMMENT '撤销原因' AFTER `revoked_at`;

UPDATE `refresh_token` SET `issued_at` = `create_time` WHERE `issued_at` IS NULL;

-- Malformed legacy rows with neither plaintext nor hash can never authenticate. Give them a
-- deterministic, non-secret tombstone hash so NOT NULL/UNIQUE can be enforced safely.
UPDATE `refresh_token`
SET `token_hash` = LOWER(SHA2(CONCAT('retired-refresh:', `id`, ':', `create_time`), 256)),
    `revoked_at` = COALESCE(`revoked_at`, NOW()),
    `revocation_reason` = COALESCE(`revocation_reason`, 'INVALID_LEGACY_TOKEN')
WHERE `token_hash` IS NULL OR `token_hash` = '';

-- Existing access JWTs have no server-side session id. Revoke all legacy families at cut-over.
UPDATE `refresh_token`
SET `revoked_at` = COALESCE(`revoked_at`, NOW()),
    `revocation_reason` = COALESCE(`revocation_reason`, 'SECURITY_MIGRATION')
WHERE `revoked_at` IS NULL;

ALTER TABLE `refresh_token`
    MODIFY COLUMN `token_hash` VARCHAR(128) NOT NULL COMMENT 'Token SHA-256 哈希',
    MODIFY COLUMN `token_family_id` VARCHAR(64) NOT NULL COMMENT 'Token 家族ID',
    MODIFY COLUMN `issued_at` DATETIME NOT NULL COMMENT '签发时间';

ALTER TABLE `refresh_token` DROP INDEX `idx_refresh_token_hash`;
ALTER TABLE `refresh_token`
    ADD UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
    ADD KEY `idx_refresh_family_active` (`token_family_id`, `revoked_at`, `expire_time`),
    ADD CONSTRAINT `chk_refresh_plaintext_empty` CHECK (`token` IS NULL);
