#!/bin/bash
# 在线课程平台 Docker 部署脚本
# 可在任意已配置 Docker 的部署主机运行。

set -e  # 遇到错误立即退出

echo "========================================="
echo "   在线课程平台 Docker 部署脚本"
echo "========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查 Docker 是否安装
echo -e "${YELLOW}[1/8] 检查 Docker 环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装${NC}"
    exit 1
fi

if ! command -v docker compose &> /dev/null; then
    echo -e "${RED}错误: Docker Compose 未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker 环境正常${NC}"
echo ""

# 检查并创建 local_net 网络
echo -e "${YELLOW}[2/8] 检查 Docker 网络...${NC}"
if ! docker network inspect local_net >/dev/null 2>&1; then
    echo "创建 local_net 网络..."
    docker network create local_net
    echo -e "${GREEN}✓ local_net 网络已创建${NC}"
else
    echo -e "${GREEN}✓ local_net 网络已存在${NC}"
fi
echo ""

# 检查环境变量文件
echo -e "${YELLOW}[3/8] 检查环境配置...${NC}"
if [ ! -f .env ]; then
    echo -e "${YELLOW}未找到 .env 文件，从示例创建...${NC}"
    cp .env.example .env
    echo -e "${RED}请编辑 .env 文件并配置实际的密码和密钥！${NC}"
    echo -e "${RED}然后重新运行此脚本。${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 环境配置文件存在${NC}"
echo ""

# 停止旧容器（如果存在）
echo -e "${YELLOW}[4/8] 停止旧容器...${NC}"
docker compose down || true
echo -e "${GREEN}✓ 旧容器已停止${NC}"
echo ""

# 构建镜像
echo -e "${YELLOW}[5/8] 构建 Docker 镜像...${NC}"
echo "这可能需要几分钟时间..."
docker compose build --no-cache
echo -e "${GREEN}✓ 镜像构建完成${NC}"
echo ""

# 启动服务
echo -e "${YELLOW}[6/8] 启动服务...${NC}"
docker compose up -d
echo -e "${GREEN}✓ 服务已启动${NC}"
echo ""

# 等待服务健康
echo -e "${YELLOW}[7/8] 等待服务就绪...${NC}"
echo "等待 MySQL 启动 (最多 60 秒)..."
for i in {1..60}; do
    if docker compose exec -T mysql sh -c 'mysqladmin ping -h localhost -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; then
        echo -e "${GREEN}✓ MySQL 已就绪${NC}"
        break
    fi
    echo -n "."
    sleep 1
done
echo ""

echo "等待后端服务启动 (最多 90 秒)..."
for i in {1..90}; do
    if curl -f http://localhost:8082/api/health >/dev/null 2>&1; then
        echo -e "${GREEN}✓ 后端服务已就绪${NC}"
        break
    fi
    echo -n "."
    sleep 1
done
echo ""

echo "等待前端服务启动 (最多 30 秒)..."
for i in {1..30}; do
    if curl -f http://localhost:8888/health >/dev/null 2>&1; then
        echo -e "${GREEN}✓ 前端服务已就绪${NC}"
        break
    fi
    echo -n "."
    sleep 1
done
echo ""

# 显示服务状态
echo -e "${YELLOW}[8/8] 检查服务状态...${NC}"
docker compose ps
echo ""

# 部署完成
echo "========================================="
echo -e "${GREEN}   部署完成！${NC}"
echo "========================================="
echo ""
echo "服务访问地址："
echo "  - 前端: http://localhost:8888"
echo "  - 后端: http://localhost:8082/api"
echo "  - API文档: http://localhost:8082/api/doc.html"
echo ""
echo "公网访问地址由部署环境的反向代理配置决定。"
echo ""
echo "查看日志："
echo "  docker compose logs -f [service_name]"
echo ""
echo "停止服务："
echo "  docker compose down"
echo ""
