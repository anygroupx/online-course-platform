-- ===========================================
-- 主库MySQL配置优化
-- 用途: 开启binlog并配置主从复制
-- 执行前请备份当前配置！
-- ===========================================

-- 注意: 以下配置需要在 my.cnf 中修改，不能在SQL中执行
-- 文件位置: /etc/my.cnf 或 /etc/mysql/my.cnf

/*
[mysqld]
# ========== 服务器标识 ==========
server-id = 1  # 主库ID，必须唯一

# ========== Binlog配置 ==========
log-bin = mysql-bin  # 开启binlog
binlog_format = ROW  # 行模式，数据一致性最好
max_binlog_size = 500M  # 单个binlog文件最大大小
expire_logs_days = 7  # binlog保留天数
binlog_cache_size = 4M  # binlog缓存大小

# 同步设置（建议）
sync_binlog = 1  # 每次事务提交都刷新binlog到磁盘
innodb_flush_log_at_trx_commit = 1  # 每次事务提交都刷新redo log

# 需要复制的数据库
binlog-do-db = online_course

# 跳过的数据库（系统库）
binlog-ignore-db = mysql
binlog-ignore-db = information_schema
binlog-ignore-db = performance_schema
binlog-ignore-db = sys

# ========== 性能优化 ==========
max_connections = 500  # 最大连接数
innodb_buffer_pool_size = 1G  # InnoDB缓冲池（根据服务器内存调整，建议为总内存的50-70%）
innodb_log_file_size = 256M  # redo log大小
innodb_flush_method = O_DIRECT  # 避免双重缓冲

# ========== 字符集 ==========
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# ========== 时区 ==========
default-time-zone = '+08:00'
*/

-- ===========================================
-- 创建复制用户（在主库执行）
-- ===========================================

-- 1. 创建复制专用用户
CREATE USER IF NOT EXISTS 'repl_user'@'%' IDENTIFIED BY '<SET_A_STRONG_PASSWORD>';

-- 2. 授予复制权限
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl_user'@'%';

-- 3. 刷新权限
FLUSH PRIVILEGES;

-- 4. 查看主库状态（记录File和Position，配置从库时需要）
SHOW MASTER STATUS;

-- 预期输出示例:
-- +------------------+----------+--------------+---------------------------------------------+
-- | File             | Position | Binlog_Do_DB | Binlog_Ignore_DB                            |
-- +------------------+----------+--------------+---------------------------------------------+
-- | mysql-bin.000001 |      154 | online_course| mysql,information_schema,performance_schema |
-- +------------------+----------+--------------+---------------------------------------------+

-- ===========================================
-- 验证配置
-- ===========================================

-- 检查binlog是否开启
SHOW VARIABLES LIKE 'log_bin';  -- 应该显示 ON

-- 检查server_id
SHOW VARIABLES LIKE 'server_id';  -- 应该显示 1

-- 查看binlog格式
SHOW VARIABLES LIKE 'binlog_format';  -- 应该显示 ROW

-- 查看当前binlog文件
SHOW BINARY LOGS;

-- ===========================================
-- 常用管理命令
-- ===========================================

-- 清理过期binlog（谨慎使用）
-- PURGE BINARY LOGS BEFORE '2025-01-01 00:00:00';

-- 查看binlog事件（调试用）
-- SHOW BINLOG EVENTS IN 'mysql-bin.000001' LIMIT 10;

-- 刷新binlog（生成新文件）
-- FLUSH LOGS;
