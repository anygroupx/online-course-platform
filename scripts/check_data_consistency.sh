#!/bin/bash
# ===========================================
# 主从数据一致性校验脚本
# 功能: 使用pt-table-checksum校验主从数据
# 作者: AI Assistant
# 依赖: percona-toolkit
# ===========================================

# 连接信息必须由调用环境提供，禁止在脚本中保存数据库凭据。
: "${MASTER_HOST:?请设置 MASTER_HOST}"
: "${MASTER_PASSWORD:?请设置 MASTER_PASSWORD}"
MASTER_PORT="${MASTER_PORT:-13306}"
MASTER_USER="${MASTER_USER:-root}"

DATABASE="online_course"

echo "======================================"
echo "MySQL主从数据一致性校验"
echo "======================================"
echo "开始时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 检查是否安装percona-toolkit
if ! command -v pt-table-checksum &> /dev/null; then
    echo "❌ 错误: 未安装percona-toolkit"
    echo "安装方法:"
    echo "  CentOS: yum install percona-toolkit"
    echo "  Ubuntu: apt-get install percona-toolkit"
    exit 1
fi

# 执行校验
echo "正在校验数据库: $DATABASE..."
pt-table-checksum \
  --host=$MASTER_HOST \
  --port=$MASTER_PORT \
  --user=$MASTER_USER \
  --password="$MASTER_PASSWORD" \
  --databases=$DATABASE \
  --no-check-binlog-format \
  --replicate=percona.checksums

echo ""
echo "======================================"
echo "校验完成: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""
echo "查看结果: SELECT * FROM percona.checksums WHERE this_crc <> master_crc OR master_cnt <> this_cnt;"
