-- 040_schema_migration.sql
-- 迁移账本：让数据库自己记录跑过哪些迁移。
--
-- 背景：034-039 之前全部手工执行，生产库有 72 张表却没有任何版本记录，
-- 无法判断某个迁移是否已经应用。本表只记录事实，不改变业务结构。
--
-- ledger 自举不回填历史：生产已应用的 001-039 一次性登记为 adopt 记录，
-- 由 scripts/migrate.sh adopt 完成，避免把「当时到底跑过什么」猜进账本。

CREATE TABLE IF NOT EXISTS schema_migration (
  version      VARCHAR(160) NOT NULL COMMENT '迁移文件名，如 040_schema_migration.sql',
  source       VARCHAR(40)  NOT NULL COMMENT 'apply=正常执行 adopt=存量登记 baseline=基线回放',
  checksum     CHAR(64)     NOT NULL COMMENT 'SQL 文件的 SHA256，用于发现账本与实际不一致',
  applied_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  applied_by   VARCHAR(120) NOT NULL DEFAULT '' COMMENT '执行者标识，留空表示未知',
  PRIMARY KEY (version),
  KEY idx_schema_migration_applied_at (applied_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='迁移账本，记录已应用的 database/migrations 文件';
