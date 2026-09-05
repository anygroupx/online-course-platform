-- Apply once to an existing database BEFORE deploying the new backend.
-- Existing active providers remain active (their saved admin configuration is the authorization).
-- Changing their URL, type or credentials will require a new verification. No verification is fabricated.
ALTER TABLE `api_provider`
    MODIFY COLUMN `api_url` VARCHAR(2048) DEFAULT NULL COMMENT '规范化API基础地址，不含query/fragment',
    MODIFY COLUMN `password` TEXT DEFAULT NULL COMMENT '加密密码',
    MODIFY COLUMN `token` TEXT DEFAULT NULL COMMENT '加密Token',
    MODIFY COLUMN `api_key` TEXT DEFAULT NULL COMMENT '加密API Key',
    ADD COLUMN `config_version` BIGINT NOT NULL DEFAULT 0 COMMENT '配置CAS版本，防止旧测试结果授权新地址',
    ADD COLUMN `verified_at` DATETIME NULL COMMENT '最近成功的人工连接验证时间',
    ADD COLUMN `verified_by` BIGINT NULL COMMENT '连接验证操作人',
    ADD COLUMN `checked_at` DATETIME NULL COMMENT '最近连接/健康检查时间',
    ADD COLUMN `last_check_reason` VARCHAR(40) NULL COMMENT '安全的健康检查分类，不存上游响应',
    ADD COLUMN `last_check_error_id` VARCHAR(36) NULL COMMENT '健康检查错误追踪ID';

UPDATE `api_provider` SET `status` = 0 WHERE `status` IS NULL OR `status` NOT IN (0, 1, 2);
ALTER TABLE `api_provider`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 2 COMMENT '状态：0-禁用 1-已启用 2-待验证/待启用';
