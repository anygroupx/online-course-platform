# 部署指南

> 更新时间：2026-07-12

本文覆盖本地进程部署与服务器 / Docker 部署。Docker 细节以 [DOCKER_DEPLOY.md](../DOCKER_DEPLOY.md) 为准。

## 一、架构与端口

| 组件 | 开发 | Docker 宿主机 | 公网 |
|------|------|---------------|------|
| 前端 | 5173 | 8888 | https://course.example.com |
| 后端 | 8080/api | 8082/api | https://course.example.com/api |
| MySQL | 本机 3306 | **不映射**（容器内 3306） | 禁止公网 |
| Redis | 可选 6379 | **不映射**（容器内 6379+密码） | 禁止公网 |
| ES | 可选 9200 | **不映射**（容器内 9200） | 禁止公网 |

部署拓扑：公网 Nginx（VPS）→ WireGuard → 本机 Docker `192.0.2.11`。

## 二、Docker 部署（推荐生产）

### 前置

- Docker 20.10+ / Compose v2
- 已创建外部网络：`docker network create local_net`
- 已配置 `.env`（从 `.env.example` 复制）

### 步骤

```bash
cp .env.example .env
# 编辑 MYSQL_* / JWT_SECRET

chmod +x deploy.sh
./deploy.sh
# 或：docker compose up -d --build
```

### 验证

```bash
docker compose ps
curl http://localhost:8082/api/health
curl -I http://localhost:8888
```

### 配置挂载

- 后端生产配置：`backend/config/application-prod.yml` → 容器 `/app/config/application-prod.yml`
- 后端 profile：`SPRING_PROFILES_ACTIVE=prod`
- 后端监听：**8082** + `context-path=/api`

### 数据迁移

新库：依赖 MySQL 初始化挂载的 `schema.sql`。
存量库：手动执行 `database/migrations/*.sql`。

## 三、VPS Nginx

配置样例：`deploy/vps-nginx-course.conf`

要点：

1. SSL（Let’s Encrypt / certbot）
2. `/` → 前端 `http://192.0.2.11:8888`
3. `/api/` → 后端 `http://192.0.2.11:8082/api/`
4. WebSocket / 大 body 按需调整
5. 证书与域名：`course.example.com`

监控配置见 `deploy/UPTIME_KUMA_SETUP.md`。

## 四、非 Docker 进程部署

### 后端

```bash
cd backend
mvn clean package -DskipTests -pl course-web -am
java -jar course-web/target/*.jar --spring.profiles.active=prod
```

确保：

- 生产配置中数据源、JWT、日志路径正确
- 反向代理指向 `8082`（或你修改后的端口）

### 前端

```bash
cd frontend
npm ci
npm run build
# 将 dist/ 交给 Nginx root
```

可参考：

- `frontend/deploy/baota-deploy.md`
- `frontend/deploy/manual-deploy.md`

## 五、环境变量清单

| 变量 | 说明 |
|------|------|
| `MYSQL_ROOT_PASSWORD` | MySQL root（Compose 必填） |
| `MYSQL_PASSWORD` | 应用库密码（Compose 必填） |
| `JWT_SECRET` | JWT 密钥（Compose 必填） |
| `REDIS_PASSWORD` | Redis 密码 |
| `APP_CRYPTO_SECRET` | 敏感配置加解密密钥 |
| `API_DOC_ENABLED` | 是否开启 Knife4j |
| `SPRING_DATASOURCE_*` | 数据源覆盖 |
| `SPRING_DATA_REDIS_HOST` | Redis |
| `SPRING_ELASTICSEARCH_URIS` | ES |

## 六、安全检查清单

- [ ] 修改默认管理员密码 `admin/123456`
- [ ] 生产关闭或限制 API 文档
- [ ] JWT_SECRET 使用高强度随机串
- [ ] MySQL 不映射到公网（或仅 VPN）
- [ ] HTTPS 强制跳转
- [ ] 定期备份 MySQL 数据卷
- [ ] 确认 `allowed-origins` 仅包含可信域名

## 七、回滚与日志

```bash
# 日志
docker compose logs -f backend
docker compose logs -f frontend

# 回滚镜像（示例）
docker compose pull   # 若使用仓库镜像
docker compose up -d

# 数据卷备份
docker run --rm -v online-course-platform_mysql_data:/var/lib/mysql -v $(pwd):/backup alpine \
  tar czf /backup/mysql-backup.tgz /var/lib/mysql
```

## 八、与旧文档差异

| 旧描述 | 当前 |
|--------|------|
| 后端端口 8080（生产） | 容器生产 **8082** |
| 单模块 jar | `course-web` 模块 jar |
| 无 ES | Compose 含 Elasticsearch |
| 子模块仓库 | 已合并为单仓 |
