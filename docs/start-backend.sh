#!/bin/bash

echo "========================================"
echo "   在线网课平台 - 后端启动脚本"
echo "========================================"
echo ""

cd backend

echo "[1/2] 检查Maven环境..."
if ! command -v mvn &> /dev/null; then
    echo "[错误] 未检测到Maven，请先安装Maven"
    exit 1
fi

echo "[2/2] 启动Spring Boot应用..."
echo ""
mvn spring-boot:run

