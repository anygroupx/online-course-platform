@echo off
REM 在线课程平台 Docker 部署脚本 (Windows)
REM 可在任意已配置 Docker 的部署主机运行。

echo =========================================
echo    在线课程平台 Docker 部署脚本
echo =========================================
echo.

REM 检查 Docker 是否安装
echo [1/8] 检查 Docker 环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker 未安装或未启动
    pause
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo 错误: Docker Compose 未安装
    pause
    exit /b 1
)

echo [OK] Docker 环境正常
echo.

REM 检查并创建 local_net 网络
echo [2/8] 检查 Docker 网络...
docker network inspect local_net >nul 2>&1
if errorlevel 1 (
    echo 创建 local_net 网络...
    docker network create local_net
    echo [OK] local_net 网络已创建
) else (
    echo [OK] local_net 网络已存在
)
echo.

REM 检查环境变量文件
echo [3/8] 检查环境配置...
if not exist .env (
    echo 未找到 .env 文件，从示例创建...
    copy .env.example .env
    echo 请编辑 .env 文件并配置实际的密码和密钥！
    echo 然后重新运行此脚本。
    pause
    exit /b 1
)
echo [OK] 环境配置文件存在
echo.

REM 停止旧容器
echo [4/8] 停止旧容器...
docker compose down 2>nul
echo [OK] 旧容器已停止
echo.

REM 构建镜像
echo [5/8] 构建 Docker 镜像...
echo 这可能需要几分钟时间...
docker compose build --no-cache
if errorlevel 1 (
    echo 镜像构建失败
    pause
    exit /b 1
)
echo [OK] 镜像构建完成
echo.

REM 启动服务
echo [6/8] 启动服务...
docker compose up -d
if errorlevel 1 (
    echo 服务启动失败
    pause
    exit /b 1
)
echo [OK] 服务已启动
echo.

REM 等待服务健康
echo [7/8] 等待服务就绪...
echo 等待服务启动... (约60秒)
timeout /t 60 /nobreak >nul
echo [OK] 服务应该已经启动
echo.

REM 显示服务状态
echo [8/8] 检查服务状态...
docker compose ps
echo.

REM 部署完成
echo =========================================
echo    部署完成！
echo =========================================
echo.
echo 服务访问地址：
echo   - 前端: http://localhost:8888
echo   - 后端: http://localhost:8082/api
echo   - API文档: http://localhost:8082/api/doc.html
echo.
echo 公网访问地址由部署环境的反向代理配置决定。
echo.
echo 查看日志：
echo   docker compose logs -f [service_name]
echo.
echo 停止服务：
echo   docker compose down
echo.
pause
