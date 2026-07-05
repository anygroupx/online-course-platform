@echo off
chcp 65001 >nul
echo ========================================
echo    在线网课平台 - 前端启动脚本
echo ========================================
echo.

cd frontend

echo [1/3] 检查Node.js环境...
call node -v >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到Node.js，请先安装Node.js
    pause
    exit /b 1
)

echo [2/3] 检查依赖是否安装...
if not exist "node_modules" (
    echo [信息] 首次运行，正在安装依赖...
    call npm install
)

echo [3/3] 启动开发服务器...
echo.
call npm run dev

pause

