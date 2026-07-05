#!/bin/bash
# ===========================================
# MySQL主从复制监控脚本
# 功能: 检查主从复制状态和延迟
# 作者: AI Assistant
# ===========================================

# ========== 配置区域 ==========
SLAVE_HOST="SLAVE_IP_ADDRESS"  # 从库IP地址（请修改）
SLAVE_PORT="13306"
SLAVE_USER="root"
SLAVE_PASS="YOUR_PASSWORD"  # 从库密码（请修改）

# 告警阈值
MAX_DELAY_SECONDS=10  # 最大允许延迟（秒）
ALERT_WEBHOOK="https://your-alert-webhook.com"  # 钉钉/企业微信webhook（可选）

# 日志文件
LOG_FILE="/var/log/mysql_replication_monitor.log"

# ========== 函数定义 ==========

# 发送告警
send_alert() {
    local message=$1
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ALERT: $message" | tee -a $LOG_FILE
    
    # 钉钉/企业微信告警（可选）
    # curl -X POST "$ALERT_WEBHOOK" \
    #   -H 'Content-Type: application/json' \
    #   -d "{\"msgtype\":\"text\",\"text\":{\"content\":\"MySQL主从告警: $message\"}}"
}

# 记录日志
log_info() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] INFO: $1" >> $LOG_FILE
}

# ========== 主逻辑 ==========

log_info "开始检查主从复制状态..."

# 连接从库查询状态
SLAVE_STATUS=$(mysql -h$SLAVE_HOST -P$SLAVE_PORT -u$SLAVE_USER -p$SLAVE_PASS \
  -e "SHOW SLAVE STATUS\G" 2>&1)

# 检查连接是否成功
if [ $? -ne 0 ]; then
    send_alert "无法连接到从库 $SLAVE_HOST:$SLAVE_PORT"
    exit 1
fi

# 提取关键指标
IO_RUNNING=$(echo "$SLAVE_STATUS" | grep "Slave_IO_Running:" | awk '{print $2}')
SQL_RUNNING=$(echo "$SLAVE_STATUS" | grep "Slave_SQL_Running:" | awk '{print $2}')
SECONDS_BEHIND=$(echo "$SLAVE_STATUS" | grep "Seconds_Behind_Master:" | awk '{print $2}')
LAST_ERROR=$(echo "$SLAVE_STATUS" | grep "Last_Error:" | cut -d':' -f2-)

# 检查IO线程
if [ "$IO_RUNNING" != "Yes" ]; then
    send_alert "从库IO线程未运行！状态: $IO_RUNNING | 错误: $LAST_ERROR"
    exit 1
fi

# 检查SQL线程
if [ "$SQL_RUNNING" != "Yes" ]; then
    send_alert "从库SQL线程未运行！状态: $SQL_RUNNING | 错误: $LAST_ERROR"
    exit 1
fi

# 检查延迟
if [ "$SECONDS_BEHIND" = "NULL" ]; then
    send_alert "主从复制延迟为NULL，可能网络中断"
    exit 1
elif [ "$SECONDS_BEHIND" -gt "$MAX_DELAY_SECONDS" ]; then
    send_alert "主从延迟过高: ${SECONDS_BEHIND}秒 (阈值: ${MAX_DELAY_SECONDS}秒)"
    # 不退出，仅告警
fi

# 一切正常
log_info "主从复制状态正常 | IO: $IO_RUNNING | SQL: $SQL_RUNNING | 延迟: ${SECONDS_BEHIND}秒"

exit 0
