# 部署指南

> 更新时间：2026-09-12（补齐030–032发布前置；下方保留2026-09-11历史发布记录）

本文覆盖本地进程部署与服务器 / Docker 部署。Docker 细节以 [DOCKER_DEPLOY.md](../DOCKER_DEPLOY.md) 为准。

## 2026-09-11 生产发布记录

2026-09-11 已将验收版本 `native-ui-20260911-160544` 发布至 [course.csuft.tech](https://course.csuft.tech)，完成 13 份所需迁移并开启服务总开关及自动状态核对。只替换前后端容器；基础服务、代理配置和认证/加密密钥保持不变。通知和人脸采集仍未放开。

30 项 HTTP 检查、静态资源一致性、自动核对 SQL 及桌面/手机登录页加载检查通过；没有绕过登录或提交真实交易。发布时服务商品和项目配置为空，需管理员完成业务配置与审核后才能正式下单；这不是全部插件功能或真实业务协议的验收完成声明。

产物、迁移与应用回滚说明见 [Docker 发布记录](../DOCKER_DEPLOY.md#2026-09-11-发布与启用记录)。下文为后续部署操作指南，不表示可跳过授权、备份及目标库核查。

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

> 先完成“数据迁移”小节：新版后端需要核查030–032，即使关闭自动核对也不能跳过。所有发布/迁移操作须获得明确授权。

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

新库仅初始化挂载的 `schema.sql`，仍须审核所需增量迁移；存量库根据真实版本、备份及明确授权手动执行 `database/migrations/*.sql`，应用不会自动执行018-032。

**包含自动进度核对的新后端必须先完整应用 `030_native_service_status_refresh.sql`，即使业务及任务开关均关闭。** 实体查询依赖新增7列；030只增加维护元数据、约束与索引，不改历史金额/状态/业务版本。计价前置脚本是 `029_native_service_price_precision.sql`，不要与另一个编号029脚本混淆。MySQL DDL中途失败须核查并修复实际结构，不能盲目重跑。

还须核查031的 `course_order.course_id VARCHAR(2048)` 和032的 `course_order_progress_log` 表及订单时间索引。032是课程订单进度同步/查询的部署前置，与原生服务开关无关；结构已存在时不要重跑一次性DDL，应用回滚保留字段与日志。

迁移完成后才发布新前后端，配置模板中的任务默认关闭；总开关和任务开关同时开启才会自动核对。独立线程每60秒调度、每批至多10单；手动与自动共用5分钟逐单租约，失败退避至60分钟。不自动下单、退款或通知，不保证执行时效。配置只在后端启动时读取；变更/回滚需授权发布，保留新增列及记录。详细步骤见 [Docker部署文档](../DOCKER_DEPLOY.md#新版订单发布前置)。

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
| `NATIVE_SERVICES_ENABLED` | 原生服务总开关，默认false；须核查018-032与授权验收 |
| `NATIVE_SERVICE_STATUS_REFRESH_ENABLED` | 闪电、黑鲨、极光、无心、实习计划、AppUI、雷电和鲸鱼只读自动核对；默认false且依赖总开关，实习不推断考勤；部署新版后端前必须完成030 |
| `NATIVE_SERVICE_NOTIFICATIONS_ENABLED` | 独立通知开关，默认false；与进度核对无关 |
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
