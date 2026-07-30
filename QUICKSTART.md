# 快速开始指南

> 更新时间：2026-07-12
> 适用：Docker Compose 一键部署

## 一分钟部署

### Windows

1. 启动 Docker Desktop
2. 双击或执行 `deploy.bat`
3. 等待构建完成（首次 5–15 分钟）
4. 访问：
   - 本地：http://localhost:8888
   - 域名：https://course.example.com

### Linux / macOS

```bash
chmod +x deploy.sh
./deploy.sh
```

## 部署前检查

- [ ] Docker / Docker Compose 可用
- [ ] 已执行：`docker network create local_net`
- [ ] 已复制并编辑：`cp .env.example .env`
- [ ] 端口空闲：`8082`、`8888`（MySQL/Redis/ES 仅容器内网，不占宿主机端口）
- [ ] （跨机访问）WireGuard 已连通，本机服务 IP 规划为 `192.0.2.11`

## 环境变量

```bash
cp .env.example .env
```

必改（无弱默认，未设置将导致 compose 启动失败）：

```env
MYSQL_ROOT_PASSWORD=强密码
MYSQL_PASSWORD=应用库密码
JWT_SECRET=至少32字节随机串
REDIS_PASSWORD=Redis密码
APP_CRYPTO_SECRET=本地AES主密钥
```

可选：

```env
API_DOC_ENABLED=false
```

## 服务地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8888 |
| 后端健康检查 | http://localhost:8082/api/health |
| 后端 API | http://localhost:8082/api |
| MySQL / Redis / ES | 仅 `local_net` 容器网络（无宿主机端口映射） |

默认管理员：`admin` / `123456`（登录后立刻修改）。

## 常用命令

```bash
docker compose ps
docker compose logs -f backend
docker compose restart backend
docker compose down          # 停服务，保留数据卷
docker compose down -v       # 危险：删除数据卷
```

重建：

```bash
./rebuild-docker.sh
# 或 Windows: rebuild-docker.bat
```

## VPS Nginx（首次）

1. 使用 `deploy/vps-nginx-course.conf`
2. 反代前端 `192.0.2.11:8888`，API `192.0.2.11:8082`
3. `certbot` 申请 `course.example.com` 证书
4. 监控项配置见 `deploy/UPTIME_KUMA_SETUP.md`

## 本地开发（非 Docker）

见 [docs/QUICK_START.md](./docs/QUICK_START.md)。

关键命令：

```bash
# 后端
cd backend && mvn -pl course-web -am spring-boot:run   # :8080

# 前端
cd frontend && npm install && npm run dev              # :5173
```

## 更多文档

- [DOCKER_DEPLOY.md](./DOCKER_DEPLOY.md)
- [docs/DEPLOYMENT_GUIDE.md](./docs/DEPLOYMENT_GUIDE.md)
- [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)
- [docs/README.md](./docs/README.md)
