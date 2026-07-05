-- 添加上次同步时间戳字段到 api_provider 表
ALTER TABLE `api_provider`
ADD COLUMN `last_sync_time` BIGINT DEFAULT NULL COMMENT '上次同步时间戳（秒）' AFTER `balance`;
