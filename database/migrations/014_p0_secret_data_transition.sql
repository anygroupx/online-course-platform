-- 014 P0: finish API Key plaintext transition and document student credential migration.
-- API keys are one-way credentials: backfill SHA-256 for legacy values then erase plaintext.
UPDATE `sys_user`
SET `api_key_hash` = LOWER(SHA2(`api_key`, 256)),
    `api_key_prefix` = LEFT(`api_key`, 8),
    `api_key_scopes` = COALESCE(NULLIF(`api_key_scopes`, ''), 'balance:read,orders:read,orders:write,platforms:read'),
    `api_key_expire_time` = COALESCE(`api_key_expire_time`, DATE_ADD(NOW(), INTERVAL 1 YEAR))
WHERE `api_key` IS NOT NULL
  AND `api_key` <> ''
  AND `api_key` <> '0'
  AND (`api_key_hash` IS NULL OR `api_key_hash` = '');

UPDATE `sys_user`
SET `api_key` = NULL
WHERE `api_key_hash` IS NOT NULL AND `api_key_hash` <> '';

-- Student credentials are reversible integration credentials and cannot be one-way hashed.
-- Until field encryption is deployed, access is limited to the docking subsystem and API/export
-- serialization is denied. The encryption key must live outside MySQL (secret manager/env mount).
-- Historical plaintext must be encrypted in-place in a dedicated, separately rehearsed migration;
-- never place the encryption key or plaintext backup in this database.

-- Preserve the legacy balance endpoint while making its authorization explicit.
UPDATE `sys_user`
SET `api_key_scopes` = CONCAT_WS(',', NULLIF(`api_key_scopes`, ''), 'balance:read')
WHERE `api_key_hash` IS NOT NULL
  AND FIND_IN_SET('balance:read', COALESCE(`api_key_scopes`, '')) = 0;
