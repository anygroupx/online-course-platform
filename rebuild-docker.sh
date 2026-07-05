#!/bin/bash
# Docker 彻底重新构建脚本 (Linux/Mac)
# Source: AURA-X-KYS - 解决环境变量缓存问题

set -e

echo "===================================="
echo "Docker 彻底重新构建"
echo "===================================="

echo ""
echo "[1/5] 停止并删除容器..."
docker-compose down

echo ""
echo "[2/5] 删除前端镜像..."
docker rmi course-platform-frontend:latest 2>/dev/null || true

echo ""
echo "[3/5] 删除后端镜像..."
docker rmi course-platform-backend:latest 2>/dev/null || true

echo ""
echo "[4/5] 清理构建缓存..."
docker builder prune -f

echo ""
echo "[5/5] 无缓存重新构建并启动..."
docker-compose build --no-cache
docker-compose up -d

echo ""
echo "===================================="
echo "完成! 正在查看容器状态..."
echo "===================================="
docker-compose ps

echo ""
echo "查看前端日志: docker-compose logs -f frontend"
echo "查看后端日志: docker-compose logs -f backend"
