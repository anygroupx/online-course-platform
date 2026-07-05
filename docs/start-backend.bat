@echo off
chcp 65001 >nul
echo ========================================
echo    在线网课平台 - 后端启动脚本
echo ========================================
echo.

cd backend

echo [1/2] 检查Maven环境...
call mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到Maven，请先安装Maven
    pause
    exit /b 1
)

echo [2/2] 启动Spring Boot应用...
echo.
call mvn spring-boot:run

pause

