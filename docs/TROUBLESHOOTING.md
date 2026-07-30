# 故障排查

> 更新时间：2026-07-12

## 1. 后端启动失败

### 1.1 多模块打包错误

**现象**：在 `backend/` 执行 `mvn spring-boot:run` 失败，或找不到主类。

**原因**：父 POM 为聚合工程。

**处理**：

```bash
cd backend
mvn -pl course-web -am spring-boot:run
# 或
mvn clean package -DskipTests -pl course-web -am
java -jar course-web/target/*.jar
```

### 1.2 端口占用

- 开发默认 **8080**
- Docker 映射 **8082**

```bash
# Linux
ss -lntp | grep -E '8080|8082'
```

### 1.3 数据库连接失败

检查：

1. MySQL 是否启动
2. 库名 `online_course` 是否存在
3. `application.yml` / 环境变量账号密码
4. `allowPublicKeyRetrieval=true` 与时区参数
5. Docker 内应使用服务名 `course-mysql`，不要写 `localhost`（除非网络模式允许）
6. Compose 安全加固后 **没有** `localhost:13306` 映射，宿主机请用 `docker exec -it course-mysql mysql ...`

### 1.4 Elasticsearch 不可用

**现象**：日志检索相关启动报错或健康检查变慢。

**处理**：

- 确保 ES 容器 healthy：`docker compose ps`
- 或开发环境调整 `spring.elasticsearch.uris` / 相关自动配置
- Compose 中 backend `depends_on: elasticsearch: service_healthy`

### 1.5 Redis 连接失败

Redis 为可选增强。若未启用：

- 注释相关配置
- 或启动 `course-redis` 容器

## 2. 前端问题

### 2.1 接口 404

- 是否带 `/api` 前缀
- 开发代理是否指向正确后端端口（8080）
- 生产 Nginx 是否把 `/api` 转到 8082

### 2.2 登录后立刻退出

- Token 过期检查：`tokenTime` / `token_expire_minutes`
- 系统配置中的 token 有效期过短
- 浏览器本地存储被清

### 2.3 管理菜单进不去

- 需要 `userInfo.isAdmin`（或角色 ADMIN）
- 路由 `meta.adminOnly`

### 2.4 样式 / 构建失败

```bash
cd frontend
rm -rf node_modules dist
npm install --legacy-peer-deps
npm run build
```

## 3. Docker 与网关

### 3.1 容器不健康

```bash
docker compose ps
docker compose logs backend --tail=200
curl -v http://localhost:8082/api/health
curl -v http://localhost:8888/health
```

### 3.2 405 Method Not Allowed / CORS

见 [DOCKER_FIX_405_CORS.md](./DOCKER_FIX_405_CORS.md)。

常见原因：

- Nginx 只放行了 GET
- 预检 OPTIONS 未正确转发
- `allowed-origins` 未包含前端域名

### 3.3 local_net 不存在

```bash
docker network create local_net
```

### 3.4 外网能开首页但 API 失败

检查 VPS Nginx：

- `/api/` 是否反代到 `192.0.2.11:8082`
- WireGuard 是否连通
- 后端 CORS / 防火墙

## 4. 业务问题

### 4.1 批量同步无更新

- 是否 Benz 类提供商（见 batch-sync 文档）
- `api_provider.last_sync_time` 是否异常
- 第三方接口凭证是否有效

### 4.2 支付回调失败

- `/api/payment/notify` 必须公网可达
- 确认在 Security 白名单中
- 查看 `payment_notify_log` 与后端日志

### 4.3 迁移执行报错（列已存在）

`007_security_hardening.sql` 等脚本对已升级库可能重复 ADD COLUMN。
手动跳过已存在语句，或先备份后按需裁剪。

## 5. 日志位置

| 环境 | 位置 |
|------|------|
| 本地开发 | `logs/online-course-platform.log`（配置项） |
| Docker | volume `backend_logs`，`docker compose logs backend` |
| 生产 yml | `/var/log/online-course-platform/application.log`（若未覆盖） |

## 6. 快速自检清单

```bash
# 1 容器
docker compose ps

# 2 健康
curl -s http://localhost:8082/api/health

# 3 登录
curl -s -X POST http://localhost:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'

# 4 前端
curl -I http://localhost:8888
```

## 7. 仍然无法解决

1. 收集：后端最近 200 行日志、`docker compose ps`、复现步骤
2. 确认文档版本与代码版本一致（后端 2.0.1 / 前端 1.1.2）
3. 核对是否混用了旧端口 8080（生产容器）与新端口 8082
