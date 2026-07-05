# ==============================================
# 在线课程平台前端部署脚本 (PowerShell)
# 目标服务器通过 SERVER_HOST 环境变量指定。
# 部署目录: /www/wwwroot/online-course-platform
# ==============================================

$ErrorActionPreference = "Stop"

# 配置变量：使用 SSH 密钥认证，禁止在脚本中保存服务器密码。
if ([string]::IsNullOrWhiteSpace($env:SERVER_HOST)) {
    throw "请设置 SERVER_HOST 环境变量"
}
$SERVER_HOST = $env:SERVER_HOST
$SERVER_USER = if ($env:SERVER_USER) { $env:SERVER_USER } else { "deploy" }
$PUBLIC_BASE_URL = if ($env:PUBLIC_BASE_URL) { $env:PUBLIC_BASE_URL } else { "https://course.example.com" }
$DEPLOY_DIR = "/www/wwwroot/online-course-platform"
$NGINX_CONF_SOURCE = ".\deploy\nginx.conf"
$NGINX_CONF_TARGET = "/www/server/panel/vhost/nginx/online-course-platform.conf"
$LOCAL_DIST_DIR = ".\dist"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "🚀 开始部署前端项目到生产服务器" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan

# 1. 检查本地 dist 目录是否存在
if (-not (Test-Path $LOCAL_DIST_DIR)) {
    Write-Host "❌ 错误: dist 目录不存在！" -ForegroundColor Red
    Write-Host "💡 请先运行 'npm run build' 构建项目" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ 检测到 dist 目录" -ForegroundColor Green

# 2. 检查是否安装了必要的工具
$hasSSH = Get-Command ssh -ErrorAction SilentlyContinue
$hasSCP = Get-Command scp -ErrorAction SilentlyContinue

if (-not $hasSSH -or -not $hasSCP) {
    Write-Host "❌ 未找到 ssh 或 scp 命令" -ForegroundColor Red
    Write-Host "💡 请确保已安装 OpenSSH 客户端" -ForegroundColor Yellow
    Write-Host "   Windows 10/11: 设置 -> 应用 -> 可选功能 -> 添加功能 -> OpenSSH 客户端" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "📦 正在部署到服务器..." -ForegroundColor Cyan

# 3. 使用已配置 SSH 密钥的 OpenSSH 客户端
Write-Host ""
Write-Host "📋 部署步骤（使用 SSH 密钥认证）：" -ForegroundColor Yellow
Write-Host ""

# 创建临时脚本文件
$tempScript = @"
#!/bin/bash
set -e

echo "📁 创建部署目录..."
mkdir -p $DEPLOY_DIR

echo "🗑️  清理旧文件..."
rm -rf $DEPLOY_DIR/*

echo "✅ 准备就绪，等待文件上传..."
"@

$tempScriptPath = Join-Path $env:TEMP "prepare_deploy.sh"
$tempScript | Out-File -FilePath $tempScriptPath -Encoding UTF8

# 步骤1: 准备服务器
Write-Host "步骤 1: 准备服务器目录" -ForegroundColor Cyan
Write-Host "执行命令: ssh $SERVER_USER@$SERVER_HOST" -ForegroundColor Gray
ssh $SERVER_USER@$SERVER_HOST "mkdir -p $DEPLOY_DIR && rm -rf $DEPLOY_DIR/*"

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 准备服务器失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ 服务器准备完成" -ForegroundColor Green

# 步骤2: 上传文件
Write-Host ""
Write-Host "步骤 2: 上传构建文件" -ForegroundColor Cyan
Write-Host "执行命令: scp -r $LOCAL_DIST_DIR\* $SERVER_USER@$SERVER_HOST`:$DEPLOY_DIR/" -ForegroundColor Gray
scp -r "$LOCAL_DIST_DIR\*" "$SERVER_USER@$SERVER_HOST`:$DEPLOY_DIR/"

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ 文件上传失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ 文件上传完成" -ForegroundColor Green

# 步骤3: 上传Nginx配置
Write-Host ""
Write-Host "步骤 3: 配置 Nginx" -ForegroundColor Cyan
Write-Host "执行命令: scp $NGINX_CONF_SOURCE $SERVER_USER@$SERVER_HOST`:$NGINX_CONF_TARGET" -ForegroundColor Gray
scp $NGINX_CONF_SOURCE "$SERVER_USER@$SERVER_HOST`:$NGINX_CONF_TARGET"

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Nginx配置上传失败" -ForegroundColor Red
    exit 1
}

# 步骤4: 重载Nginx
Write-Host ""
Write-Host "步骤 4: 重载 Nginx" -ForegroundColor Cyan
$nginxCmd = "nginx -t && systemctl reload nginx && echo 'Nginx已重载' || echo 'Nginx配置错误'"
ssh $SERVER_USER@$SERVER_HOST $nginxCmd

if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️  Nginx重载可能失败，请手动检查" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "✅ 部署完成！" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "🌐 访问地址: $PUBLIC_BASE_URL" -ForegroundColor Green
Write-Host "📋 部署目录: $DEPLOY_DIR" -ForegroundColor Gray
Write-Host "⚙️  Nginx配置: $NGINX_CONF_TARGET" -ForegroundColor Gray
Write-Host ""
Write-Host "💡 常用命令:" -ForegroundColor Yellow
Write-Host "  - 查看Nginx状态: ssh $SERVER_USER@$SERVER_HOST 'systemctl status nginx'" -ForegroundColor Gray
Write-Host "  - 查看日志: ssh $SERVER_USER@$SERVER_HOST 'tail -f /var/log/nginx/online-course-platform-access.log'" -ForegroundColor Gray
Write-Host "  - 重启Nginx: ssh $SERVER_USER@$SERVER_HOST 'systemctl restart nginx'" -ForegroundColor Gray
Write-Host "==========================================" -ForegroundColor Cyan
