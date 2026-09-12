# Docker 部署文档

> 更新时间：2026-09-12（补充新版订单迁移前置；历史发布范围与限制见下方记录）

## 2026-09-11 发布与启用记录

- 发布标识：`native-ui-20260911-160544`；访问地址：[course.csuft.tech](https://course.csuft.tech)。只替换前后端容器，MySQL、Redis、Elasticsearch 及入口代理配置未改动。
- 后端使用已验收 JAR，SHA256 为 `df4950f29044a8bb714a6291bb7b00b13ddbfe2fcfabef788c40665014b107e9`；前端以相同验收源码注入生产 Turnstile 公开 Site Key 后构建。工作区未验收的外部流水接口改动未发布。
- 完成备份、同版本 MySQL 离线结构演练及 13 份生产迁移：018–028、`029_native_service_price_precision.sql`、030。新增 22 张表，原 29 张表结构保持不变；已存在的 `029_localized_display_and_category_multiplier.sql` 未重跑。
- `NATIVE_SERVICES_ENABLED=true`、`NATIVE_SERVICE_STATUS_REFRESH_ENABLED=true`；通知开关仍为 false，人脸采集允许来源仍为空，既有加密及认证密钥保持不变。
- 验证通过：30 项本地/公网 HTTP 检查、108 个前端文件校验、匿名访问拦截（HTTP 401，业务码 -100）、自动核对实际 SQL 执行、桌面/手机登录页及验证码加载。未提交登录、真实订单、扣款或通知请求。
- **入口启用不等于商品已经可售。** 发布时服务商品和项目均未配置；须管理员配置业务账号、商品/项目、价格及必要的合同有效期并审核后，用户才能正式下单。本次发布不代表全部 PHP 包功能已完成，也不替代真实业务协议验收。

### 本次应用回滚

先在 `.env` 中将 `NATIVE_SERVICES_ENABLED` 和 `NATIVE_SERVICE_STATUS_REFRESH_ENABLED` 设为 false，保留其他配置与密钥，再执行：

```bash
docker tag course-platform-backend:rollback-native-ui-20260911-160544 course-platform-backend:latest
docker tag course-platform-frontend:rollback-native-ui-20260911-160544 course-platform-frontend:latest
docker compose up -d --no-deps --no-build --pull never --wait --wait-timeout 180 backend frontend
```

回滚只恢复应用镜像；保留新增表、字段和业务记录，不重跑迁移，也不要用旧全库备份覆盖新产生的业务数据。

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
| `NATIVE_SERVICES_ENABLED` | 原生服务商城/项目中心总开关，默认关闭；审核目标库所需迁移 018-030 并完成授权验收后启用 |
| `NATIVE_SERVICE_STATUS_REFRESH_ENABLED` | 闪电、黑鲨、极光、无心订单进度及实习计划状态自动核对；默认关闭且须总开关同时开启，实习不推断考勤；030 是部署新后端前置，不是仅开启任务的前置 |
| `NATIVE_SERVICE_NOTIFICATIONS_ENABLED` | ShowDoc 通知开关；还需迁移 023、收件人验证与发布授权 |
| `HEISHA_FACE_ALLOWED_ORIGINS` | 黑鲨官方人脸采集页允许的精确 HTTPS 来源；留空禁用 |

### 2. 网络

```bash
docker network create local_net
```

### 3. 启动

> 执行前先完成下文“新版订单发布前置”：核查 030–032 的实际表结构；关闭业务开关不能替代迁移。

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
- 已有数据：按编号和目标环境当前版本手动执行 `database/migrations/*.sql`；迁移 018-032 不会由应用自动执行，应用前须备份、审核并获得明确授权
- 卷：`mysql_data`、`redis_data`、`es_data`、`backend_logs`

### 新版订单发布前置

1. 授权核查目标库，备份并确认实际已应用的迁移；新空库仅初始化 `schema.sql`，也不能替代所需增量迁移。两个编号为029的脚本是不同变更，服务计价所需的是 `029_native_service_price_precision.sql`，不能只按编号判断已执行。
2. **部署包含新订单实体的后端前，必须完整应用 `030_native_service_status_refresh.sql`，即使两个业务开关均为false。** 它新增7个维护字段、3个约束及查询索引，不重算金额、不改历史状态/业务版本。MySQL DDL不是整文件事务；中途失败须检查实际列/约束后修复，不要盲目重跑或删除业务数据。
3. 同时核查 `031_expand_course_order_course_id.sql`（`course_order.course_id` 扩为 `VARCHAR(2048)`）及 `032_course_order_progress_log.sql`（新增 `course_order_progress_log` 和订单时间索引）。032 是部署新版课程订单查询/进度同步代码的前置，与原生服务开关无关；已有完整结构时不要重跑这两个一次性脚本。回滚应用时保留扩大后的字段、日志表和已有记录。
4. 迁移及回归验收通过后才发布新后端和前端；默认仍关闭任务。获得真实协议验收与启用授权后，才将 `NATIVE_SERVICES_ENABLED` 和 `NATIVE_SERVICE_STATUS_REFRESH_ENABLED` 同时设为true。`.env` 修改须由授权发布流程重新创建后端容器才生效，不是热开关。
5. 任务启动60秒后调度，单线程、每批至多10单；成功后至少5分钟再查，失败按5/10/20/40/60分钟退避。单批一分钟后不再领取新单，当前有界读取可继续；这不是订单完成时效承诺。
6. 仅自动读取已支持的可核对订单：闪电、黑鲨、极光、无心、实习计划、AppUI、雷电和鲸鱼；手动/后台共用5分钟逐单租约，不重下单、不退款、不通知。关闭任务只停止自动核对，不关闭手动查询或删除核对记录；回滚先关闭任务并保留新增字段，不在回滚时删表/删列。

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
