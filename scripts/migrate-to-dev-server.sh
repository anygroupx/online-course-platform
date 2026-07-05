#!/bin/bash
# 项目迁移脚本 - 在目标开发机上运行
# 从Git仓库克隆项目并完成Docker部署准备

set -e

echo "========================================="
echo "   项目迁移到开发机"
echo "========================================="
echo ""

# 配置（请根据实际修改）
GIT_REPO="https://github.com/your-username/online-course-platform.git"  # 修改为实际仓库地址
PROJECT_DIR="$HOME/online-course-platform"

# 1. 克隆或更新项目
if [ -d "$PROJECT_DIR" ]; then
    echo "[1/5] 项目目录已存在，更新代码..."
    cd "$PROJECT_DIR"
    git pull origin main  # 或 master，根据实际分支名
else
    echo "[1/5] 克隆项目..."
    cd "$HOME"
    git clone "$GIT_REPO"
    cd "$PROJECT_DIR"
fi

echo "✓ 代码已同步"
echo ""

# 2. 清理不必要的文件
echo "[2/5] 清理构建产物..."
rm -rf backend/target/
rm -rf frontend/node_modules/
rm -rf frontend/dist/
echo "✓ 清理完成"
echo ""

# 3. 设置环境变量
echo "[3/5] 配置环境变量..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo "⚠️  请编辑 .env 文件配置数据库密码和JWT密钥"
    echo "   nano .env"
else
    echo "✓ .env 文件已存在"
fi
echo ""

# 4. 创建必要的目录
echo "[4/5] 创建必要目录..."
mkdir -p mysql/conf.d
mkdir -p backend/logs
echo "✓ 目录创建完成"
echo ""

# 5. 检查Docker网络
echo "[5/5] 检查Docker网络..."
if ! docker network inspect local_net >/dev/null 2>&1; then
    echo "创建 local_net 网络..."
    docker network create local_net
else
    echo "✓ local_net 网络已存在"
fi
echo ""

echo "========================================="
echo "   迁移完成！"
echo "========================================="
echo ""
echo "下一步操作："
echo "  1. 编辑环境变量: nano .env"
echo "  2. 运行部署脚本: ./deploy.sh"
echo ""
echo "项目位置: $PROJECT_DIR"
echo ""
