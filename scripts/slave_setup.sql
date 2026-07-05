-- ===========================================
-- 从库MySQL配置优化
-- 用途: 配置从库并启动主从复制
-- 执行前请确保主库已配置完成！
-- ===========================================

-- 注意: 以下配置需要在 my.cnf 中修改

/*
[mysqld]
# ========== 服务器标识 ==========
server-id = 2  # 从库ID，必须与主库不同

# ========== Binlog配置（可选，便于日后升级） ==========
log-bin = mysql-bin
binlog_format = ROW

# ========== 中继日志 ==========
relay-log = mysql-relay-bin
relay-log-index = mysql-relay-bin.index
relay_log_recovery = 1  # 宕机恢复时自动修复中继日志

# ========== 只读模式 ==========
read_only = 1  # 普通用户只读
super_read_only = 1  # 超级用户也只读（推荐）

# ========== 复制优化 ==========
log_slave_updates = 1  # 从库也记录binlog（便于级联复制）
slave_parallel_workers = 4  # 并行复制线程数（MySQL 5.7+）
slave_parallel_type = LOGICAL_CLOCK  # 并行复制类型

# 跳过特定错误（谨慎使用）
# slave-skip-errors = 1062,1032  # 1062=主键冲突, 1032=记录不存在

# ========== 性能优化 ==========
max_connections = 500
innodb_buffer_pool_size = 1G  # 根据服务器内存调整
innodb_flush_method = O_DIRECT

# ========== 字符集 ==========
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci

# ========== 时区 ==========
default-time-zone = '+08:00'
*/

-- ===========================================
-- 配置主从复制（在从库执行）
-- ===========================================

-- 1. 停止当前复制（如果有）
STOP SLAVE;

-- 2. 重置从库状态（首次配置时）
RESET SLAVE ALL;

-- 3. 配置主库信息
-- ⚠️ 请替换以下参数为实际值：
--    - MASTER_LOG_FILE: 主库的 SHOW MASTER STATUS 中的 File
--    - MASTER_LOG_POS: 主库的 SHOW MASTER STATUS 中的 Position
CHANGE MASTER TO
  MASTER_HOST='192.0.2.10',     -- 示例地址，部署前必须替换
  MASTER_PORT=13306,             -- 主库端口
  MASTER_USER='repl_user',       -- 复制用户
  MASTER_PASSWORD='<SET_A_STRONG_PASSWORD>',     -- 部署前必须替换
  MASTER_LOG_FILE='mysql-bin.000001',  -- ⚠️ 替换为实际值
  MASTER_LOG_POS=154,                  -- ⚠️ 替换为实际值
  MASTER_CONNECT_RETRY=10,             -- 连接失败重试间隔（秒）
  MASTER_RETRY_COUNT=86400;            -- 重试次数（24小时）

-- 4. 启动复制
START SLAVE;

-- 5. 查看复制状态
SHOW SLAVE STATUS\G

-- ===========================================
-- 验证复制状态（关键字段）
-- ===========================================

/*
重点关注以下字段:

✅ Slave_IO_Running: Yes        # IO线程正常
✅ Slave_SQL_Running: Yes       # SQL线程正常
✅ Seconds_Behind_Master: 0     # 延迟时间（越小越好）
✅ Last_IO_Error: (空)          # IO错误信息
✅ Last_SQL_Error: (空)         # SQL错误信息

如果 IO 或 SQL 线程不是 Yes，检查错误信息：
- Last_IO_Error: 网络/权限问题
- Last_SQL_Error: 数据冲突/SQL执行错误
*/

-- ===========================================
-- 常见问题排查
-- ===========================================

-- 问题1: Slave_IO_Running = Connecting
-- 原因: 无法连接到主库
-- 解决: 检查网络、防火墙、用户权限
/*
-- 在主库检查用户
SELECT User, Host FROM mysql.user WHERE User = 'repl_user';

-- 测试网络连通性
-- telnet 192.0.2.10 13306
*/

-- 问题2: Slave_SQL_Running = No
-- 原因: SQL执行错误（如主键冲突）
-- 解决: 查看 Last_SQL_Error，手动修复后重启复制
/*
-- 跳过一个错误（谨慎使用）
SET GLOBAL sql_slave_skip_counter = 1;
START SLAVE;
*/

-- 问题3: Seconds_Behind_Master 很大
-- 原因: 从库性能不足或网络慢
-- 解决: 优化从库配置，启用并行复制

-- ===========================================
-- 数据一致性验证
-- ===========================================

-- 在主库插入测试数据
-- INSERT INTO sys_user (username, password) VALUES ('test_repl_001', '$2a$10$test');

-- 等待1-2秒后，在从库查询
-- SELECT * FROM sys_user WHERE username = 'test_repl_001';

-- 如果能查到，说明复制正常

-- ===========================================
-- 重建从库（当数据不一致时）
-- ===========================================

/*
1. 在主库导出数据（锁表）
mysqldump -h192.0.2.10 -P13306 -uroot -p \
  --master-data=2 \
  --single-transaction \
  --databases online_course > master_backup.sql

2. 在从库导入数据
mysql -uroot -p < master_backup.sql

3. 查看备份文件中的 MASTER_LOG_FILE 和 MASTER_LOG_POS
grep "CHANGE MASTER TO" master_backup.sql

4. 重新配置并启动复制（见上方步骤3-4）
*/

-- ===========================================
-- 管理命令
-- ===========================================

-- 停止复制
-- STOP SLAVE;

-- 启动复制
-- START SLAVE;

-- 查看复制状态
-- SHOW SLAVE STATUS\G

-- 查看复制延迟
-- SELECT TIMESTAMPDIFF(SECOND, ts, NOW()) AS delay FROM (SELECT FROM_UNIXTIME(MAX(timestamp)) AS ts FROM mysql.slave_relay_log_info) t;

-- 重置从库（清除所有复制信息）
-- STOP SLAVE;
-- RESET SLAVE ALL;
