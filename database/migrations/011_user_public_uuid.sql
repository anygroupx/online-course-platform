-- 011 用户公开 UID 升级为随机 UUID v4
-- 执行前请备份数据库。本迁移保留 sys_user.id 作为内部主键及关联键。

ALTER TABLE `sys_user`
  ADD COLUMN `uid` CHAR(36) NULL COMMENT '对外公开的随机UUID v4' AFTER `id`;

-- 为历史用户生成不含注册顺序信息的 UUID v4。
CREATE TEMPORARY TABLE `tmp_user_public_uid` AS
SELECT
  `id`,
  MD5(CONCAT(UUID(), ':', `id`, ':', RAND())) AS `hex_value`
FROM `sys_user`
WHERE `uid` IS NULL OR `uid` = '';

UPDATE `sys_user` u
JOIN `tmp_user_public_uid` t ON t.`id` = u.`id`
SET u.`uid` = LOWER(CONCAT(
  SUBSTRING(t.`hex_value`, 1, 8), '-',
  SUBSTRING(t.`hex_value`, 9, 4), '-',
  '4', SUBSTRING(t.`hex_value`, 14, 3), '-',
  '8', SUBSTRING(t.`hex_value`, 18, 3), '-',
  SUBSTRING(t.`hex_value`, 21, 12)
));

DROP TEMPORARY TABLE `tmp_user_public_uid`;

ALTER TABLE `sys_user`
  MODIFY COLUMN `uid` CHAR(36) NOT NULL COMMENT '对外公开的随机UUID v4',
  ADD UNIQUE KEY `uk_user_uid` (`uid`);
