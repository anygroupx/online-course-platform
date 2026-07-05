#!/bin/bash

# ==============================================
# 在线课程平台前端部署脚本
# 目标服务器通过 SERVER_HOST 环境变量指定。
# 部署目录: /www/wwwroot/online-course-platform
# ==============================================

set -e  # 遇到错误立即退出

# 配置变量：使用 SSH 密钥认证，禁止在脚本中保存服务器密码。
: "${SERVER_HOST:?请设置 SERVER_HOST}"
SERVER_USER="${SERVER_USER:-deploy}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://course.example.com}"
DEPLOY_DIR="/www/wwwroot/online-course-platform"
NGINX_CONF_SOURCE="./deploy/nginx.conf"
NGINX_CONF_TARGET="/www/server/panel/vhost/nginx/online-course-platform.conf"
LOCAL_DIST_DIR="./dist"

echo "=========================================="
echo "🚀 开始部署前端项目到生产服务器"
echo "=========================================="

# 1. 检查本地 dist 目录是否存在
if [ ! -d "$LOCAL_DIST_DIR" ]; then
    echo "❌ 错误: dist 目录不存在！"
    echo "💡 请先运行 'npm run build' 构建项目"
    exit 1
fi

echo "✅ 检测到 dist 目录"

# 2. 使用 SSH 密钥上传文件
echo ""
echo "📦 正在上传构建文件到服务器..."

if ! command -v ssh >/dev/null 2>&1 || ! command -v scp >/dev/null 2>&1; then
    echo "❌ 未安装 ssh 或 scp"
    exit 1
fi

# 创建远程目录
echo "📁 创建远程部署目录..."
ssh "$SERVER_USER@$SERVER_HOST" "mkdir -p '$DEPLOY_DIR'"

# 清空旧文件
echo "🗑️  清理旧文件..."
ssh "$SERVER_USER@$SERVER_HOST" "find '$DEPLOY_DIR' -mindepth 1 -maxdepth 1 -delete"

# 上传新文件
echo "⬆️  上传构建文件..."
scp -r "$LOCAL_DIST_DIR"/* "$SERVER_USER@$SERVER_HOST:$DEPLOY_DIR/"

# 上传 Nginx 配置
echo "⚙️  配置 Nginx..."
scp "$NGINX_CONF_SOURCE" "$SERVER_USER@$SERVER_HOST:$NGINX_CONF_TARGET"

# 测试并重载 Nginx
echo "🔄 重载 Nginx 配置..."
ssh "$SERVER_USER@$SERVER_HOST" << 'EOF'
# 测试 Nginx 配置
if nginx -t; then
    echo "✅ Nginx 配置测试通过"
    systemctl reload nginx
    echo "✅ Nginx 已重载"
else
    echo "❌ Nginx 配置错误"
    exit 1
fi
EOF

echo ""
echo "=========================================="
echo "✅ 部署完成！"
echo "=========================================="
echo "🌐 访问地址: $PUBLIC_BASE_URL"
echo "📋 部署目录: $DEPLOY_DIR"
echo "⚙️  Nginx配置: $NGINX_CONF_TARGET"
echo ""
echo "💡 常用命令:"
echo "  - 查看Nginx状态: systemctl status nginx"
echo "  - 查看日志: tail -f /var/log/nginx/online-course-platform-access.log"
echo "  - 重启Nginx: systemctl restart nginx"
echo "=========================================="
