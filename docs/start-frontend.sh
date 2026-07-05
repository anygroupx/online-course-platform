#!/bin/bash

echo "========================================"
echo "   在线网课平台 - 前端启动脚本"
echo "========================================"
echo ""

cd frontend

echo "[1/3] 检查Node.js环境..."
if ! command -v node &> /dev/null; then
    echo "[错误] 未检测到Node.js，请先安装Node.js"
    exit 1
fi

echo "[2/3] 检查依赖是否安装..."
if [ ! -d "node_modules" ]; then
    echo "[信息] 首次运行，正在安装依赖..."
    npm install
fi

echo "[3/3] 启动开发服务器..."
echo ""
npm run dev

