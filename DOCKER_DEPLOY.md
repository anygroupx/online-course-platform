# Docker 部署文档

> 更新时间：2026-07-12

## 概述

使用 Docker Compose 在本地（或内网机）部署完整栈，经 WireGuard 由 VPS Nginx 对外提供 HTTPS。

```
互联网用户
    ↓ HTTPS
VPS Nginx（SSL 终止）
    ↓ WireGuard（10.10.0.0/24）
本机 Docker（建议 IP 192.0.2.11）
    ├── course-frontend   宿主机 :8888 → 80
    ├── course-backend    宿主机 :8082 → 8082  (context-path=/api)
    ├── course-mysql      仅容器网络 :3306
    ├── course-redis      仅容器网络 :6379（requirepass）
    └── course-elasticsearch 仅容器网络 :9200
```

## 服务清单

| 服务名 | 镜像/构建 | 宿主机端口 | 说明 |
|--------|-----------|------------|------|
| mysql | mysql:8.0 | 无（仅 expose 3306） | 初始化挂载 `database/schema.sql` |
| redis | redis:7 | 无（仅 expose 6379） | AOF + 密码 |
| elasticsearch | elasticsearch:8.11.0 | 无（仅 expose 9200） | 单节点 |
| backend | ./backend Dockerfile | **8082** | 多模块 `course-web` jar |
| frontend | ./frontend Dockerfile | **8888** | Nginx 托管静态资源 |

网络：`local_net`（**external: true**，需预先创建）。

## 快速部署

### 1. 环境变量

```bash
cp .env.example .env
```

| 变量 | 说明 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | root 密码（必填） |
| `MYSQL_PASSWORD` | 应用用户密码，用户 `course_user`（必填） |
| `JWT_SECRET` | JWT 密钥（必填） |
| `REDIS_PASSWORD` | Redis 密码 |
| `APP_CRYPTO_SECRET` | 敏感字段本地 AES 密钥 |
| `API_DOC_ENABLED` | 是否开启 API 文档 |

### 2. 网络

```bash
docker network create local_net
```

### 3. 启动

```bash
chmod +x deploy.sh && ./deploy.sh
# Windows: deploy.bat

# 或手动
docker compose build
docker compose up -d
```

### 4. 验证

```bash
docker compose ps
curl http://localhost:8082/api/health
# 期望 JSON 中包含 "status":"UP"

curl -I http://localhost:8888
# 或前端 health：/health（见 frontend/nginx.conf）
```

## 后端镜像构建要点

`backend/Dockerfile` 多阶段构建：

1. Maven 构建：`mvn clean package -DskipTests -pl course-web -am`
2. 运行：`eclipse-temurin:17-jre-alpine`
3. 暴露 **8082**
4. 健康检查：`curl -f http://localhost:8082/api/health`
5. 挂载：`./backend/config/application-prod.yml`

环境变量覆盖数据源、Redis、ES、JWT。

## 前端镜像构建要点

1. Node 20 构建 `npm run build`
2. Nginx alpine 托管 `dist`
3. `frontend/nginx.conf` 配置路由与 `/api` 反代（以仓库文件为准）
4. 宿主机 **8888 → 80**

## 数据与迁移

- 首次空数据卷：自动执行 `schema.sql`
- 已有数据：手动执行 `database/migrations/*.sql`
- 卷：`mysql_data`、`redis_data`、`es_data`、`backend_logs`

```bash
# 进入 MySQL（无宿主机端口，需 exec 进容器）
docker exec -it course-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" online_course
```

## VPS Nginx

样例：`deploy/vps-nginx-course.conf`

建议：

```nginx
# 伪配置示意
location / {
    proxy_pass http://192.0.2.11:8888;
}
location /api/ {
    proxy_pass http://192.0.2.11:8082/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

SSL：

```bash
certbot certonly --nginx -d course.example.com
nginx -t && systemctl reload nginx
```

本机调试反代可参考 `deploy/local-nginx-8888.conf`。

## 运维命令

```bash
# 日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql

# 重启单服务
docker compose restart backend

# 重建后端
docker compose up -d --build backend

# 停止
docker compose down

# 停止并删除卷（清空数据库）
docker compose down -v
```

## 监控

见 [deploy/UPTIME_KUMA_SETUP.md](./deploy/UPTIME_KUMA_SETUP.md)：

- https://course.example.com
- https://course.example.com/api/health
- http://192.0.2.11:8082/api/health
- http://192.0.2.11:8888/health

## 故障排查

| 问题 | 处理 |
|------|------|
| `network local_net declared as external, but could not be found` | `docker network create local_net` |
| backend 一直 unhealthy | 查 ES/MySQL 是否 healthy、日志是否连库失败 |
| 外网 API 405/CORS | [docs/DOCKER_FIX_405_CORS.md](./docs/DOCKER_FIX_405_CORS.md) |
| 前端白屏 | 查 `nginx.conf` try_files、浏览器控制台 API 地址 |
| 密码错误 | 数据卷已初始化后改 `.env` 不会改库内密码，需进容器修改或清卷 |

更多：[docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)

## 安全建议

1. 修改全部默认密码与 JWT；`.env` 中必填项不可使用占位符上线
2. 生产关闭 Knife4j（`API_DOC_ENABLED=false`）
3. MySQL/ES/Redis **不映射宿主机端口**，仅 `local_net` 可达
4. 定期备份 `mysql_data`
5. 限制 VPS 防火墙仅 80/443
