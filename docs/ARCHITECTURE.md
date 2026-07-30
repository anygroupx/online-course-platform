# 架构设计文档

> 更新时间：2026-07-12
> 与代码仓库当前结构对齐（Spring Boot 多模块 + Vue 3）

## 1. 总体架构

```
┌──────────────────────────────────────────────────────────┐
│                     用户 / 浏览器                         │
└────────────────────────────┬─────────────────────────────┘
                             │ HTTPS
                             ▼
┌──────────────────────────────────────────────────────────┐
│              VPS Nginx（SSL 终止 + 反向代理）             │
│                 course.example.com                        │
└────────────────────────────┬─────────────────────────────┘
                             │ WireGuard VPN
                             ▼
┌──────────────────────────────────────────────────────────┐
│              本机 Docker（local_net / 192.0.2.11）         │
│  ┌────────────┐  ┌────────────┐  ┌──────────┐         │
│  │ Frontend   │  │ Backend    │  │ MySQL    │         │
│  │ :8888→80   │  │ :8082/api  │  │ 内网3306 │         │
│  └────────────┘  └─────┬──────┘  └──────────┘         │
│                        │  Redis/ES 仅容器网络          │
└──────────────────────────────────────────────────────────┘
```

本地开发时，前端 Vite `:5173` 直连后端 `:8080/api`，可不经过 Nginx。

---

## 2. 后端多模块（DDD 风格）

父工程：`backend/pom.xml`
版本：`2.0.1`
打包：`pom` 聚合，可运行模块为 **`course-web`**

```
backend/
├── course-common          # 通用层：Result、常量、异常、工具
├── course-domain          # 领域层：Entity / DTO / VO
├── course-infrastructure  # 基础设施：Mapper、外部 HTTP、缓存、ES
├── course-application     # 应用层：业务服务编排
└── course-web             # 接口层：Controller、Security、启动类
```

依赖方向（只允许向下）：

```
course-web
    └── course-application
            └── course-infrastructure
                    └── course-domain
                            └── course-common
```

### 模块职责

| 模块 | 包路径示例 | 职责 |
|------|------------|------|
| course-common | `com.course.platform.common.*` | 统一响应 `Result`、错误码、系统常量 |
| course-domain | `com.course.platform.domain.entity/dto/vo` | 领域模型与传输对象 |
| course-infrastructure | `com.course.platform.infra.*` | MyBatis Mapper、第三方客户端（如 AqksApiClient）、Redis/ES |
| course-application | `com.course.platform.application.service.*` | 订单、用户、支付、导出等应用服务 |
| course-web | `com.course.platform.controller.*` | REST 接口、JWT 过滤器、全局异常、启动入口 |

启动类：

- 实际：`backend/course-web/src/main/java/com/course/platform/OnlineCoursePlatformApplication.java`
- 历史残留：`backend/src/main/java/...`（勿作为构建入口）

Docker 构建命令：

```bash
mvn clean package -DskipTests -B -pl course-web -am
# 产物：course-web/target/*.jar
```

---

## 3. 接口层与安全

### 3.1 请求链路

```
HTTP Request
  → JwtAuthenticationFilter
  → Spring Security 授权
  → Controller
  → Application Service
  → Infrastructure (Mapper / External API)
  → MySQL / Redis / ES
  → Result 统一包装
```

### 3.2 认证与鉴权

- Access Token：JWT，过期时间可读系统配置（`token_expire_minutes`）
- Refresh Token：支持刷新；安全加固后使用 token hash / family（迁移 007）
- 角色：`ADMIN` / `CS` / `USER`
- 外部开放接口：`/api/external/**`（API Key）
- 支付回调：`/api/payment/notify`、`/api/payment/return` 免登录

### 3.3 主要 Controller 一览

| Controller | 前缀 | 说明 |
|------------|------|------|
| AuthController | `/auth` | 登录登出、当前用户、刷新 Token |
| RegisterController | `/register` | 注册与邀请码 |
| UserController / UserInfoController | `/users` 等 | 用户管理、个人信息 |
| CourseOrderController | `/orders` | 用户侧订单 |
| BatchOrderController | `/orders/batch` | 批量下单 |
| AdminOrderController | `/admin/orders` | 管理员订单 / 倒计时 / 导出 |
| CourseQueryController | `/courses/query` | 查课 |
| CoursePlatformController | `/courses` | 课程平台列表 |
| CoursePlatformManageController | `/admin/platforms` | 平台管理 |
| PlatformCategoryController | `/admin/platform-categories` | 分类 |
| ApiProviderController | `/admin/api-providers` | 第三方接口配置 |
| PlatformDockingController | `/admin/docking` | 导入平台 / 批量同步 |
| AqksStudyController | `/admin/aqks` | AQKS 刷课 |
| CountdownConfigController | `/admin/countdown-config` | 倒计时配置 |
| PaymentController | `/payment` | 支付宝支付 |
| PaymentConfigController | `/admin/payment-config` 等 | 支付配置 |
| RechargeCardController | `/cards` | 充值卡密 |
| AnnouncementController | `/announcement` | 公告 |
| CustomerServiceController | 客服相关 | 会话与消息 |
| SystemConfigController | `/system/config` | 系统配置 |
| SystemVariableController | `/admin/variables` | 系统变量 |
| OperationLogController | `/logs` | 操作日志 |
| StatisticsController | `/statistics` | 统计 |
| ExternalApiController | `/external` | 对外开放 API |
| ApiKeyController | `/api-keys` | API 密钥开通 |
| SportRunController | `/sport-run` | 运动跑量（部分 TODO） |
| HealthController | `/health` `/ping` | 健康检查 |

完整路径需加 `context-path`：`/api`。

---

## 4. 前端架构

```
frontend/src/
├── api/              # Axios 接口模块（auth/order/user/...）
├── components/       # 通用组件 + 企业级表格体系
├── composables/      # useTheme / useTableComposition / useResponsive
├── config/           # 配置
├── layouts/          # 主布局
├── router/           # 路由与守卫（Token 过期、adminOnly）
├── stores/           # Pinia：user / theme / tagsView / variableStore
├── styles/themes/    # 主题
├── utils/            # 请求封装等
└── views/            # 业务页面（约 29 个）
```

### 主要页面路由

| 路由 | 页面 | 权限 |
|------|------|------|
| `/login` | 登录 | 公开 |
| `/dashboard` | 仪表盘 | 登录 |
| `/orders` | 我的订单 | 登录 |
| `/query` | 查课下单 | 登录 |
| `/recharge` | 账户充值 | 登录 |
| `/profile` | 个人中心 | 登录 |
| `/api-guide` | API 文档页 | 登录 |
| `/admin/users` | 用户管理 | 管理员 |
| `/admin/orders` | 管理员订单 | 管理员 |
| `/admin/platforms` | 平台管理 | 管理员 |
| `/admin/api-providers` | 接口管理 | 管理员 |
| `/admin/cards` | 卡密管理 | 管理员 |
| `/admin/announcements` | 公告管理 | 管理员 |
| `/admin/variables` | 系统变量 | 管理员 |
| `/admin/countdown` | 倒计时管理 | 管理员 |
| `/admin/aqks` | AQKS 刷课 | 管理员 |
| `/admin/customer-service` | 客服管理 | 管理员 |
| `/payment/*` | 支付回调 / 支付订单 | 登录 |
| `/theme-config` | 主题配置 | 登录 |

构建产物由 Nginx 托管，`/api` 反代到后端容器。

---

## 5. 数据层

### 5.1 MySQL 核心表（schema.sql）

- `sys_user`：用户 / 代理 / API Key
- `course_platform`：课程平台
- `platform_category`：平台分类（迁移含 remote 映射）
- `api_provider`：第三方 API
- `course_order`：课程订单
- `operation_log`：操作日志
- `system_config` / `system_variable`：配置与变量
- `recharge_card` / `recharge_record`：充值
- `announcement`：公告
- `customer_service_*`：客服
- `countdown_config` / `countdown_history`：倒计时
- `payment_*` / `account_ledger`：支付与资金流水（迁移）
- `aqks_study_log`：AQKS 日志（迁移 006）
- `refresh_token`：刷新令牌

详见 [DATABASE.md](./DATABASE.md)。

### 5.2 Redis

可选。Docker Compose 默认启动；本地开发配置中可注释。

### 5.3 Elasticsearch

用于操作日志等全文检索（`OperationLogSearchRepository`）。Compose 默认单节点 `8.11.0`。

---

## 6. 配置要点

| 配置文件 | 用途 |
|----------|------|
| `course-web/.../application.yml` | 开发默认（port **8080**） |
| `course-web/.../application-prod.yml` | 生产 profile |
| `backend/config/application-prod.yml` | Docker 挂载覆盖 |
| 根目录 `.env` | Compose 密码 / JWT 等 |

环境变量（Compose）：

- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_ELASTICSEARCH_URIS`
- `JWT_SECRET`

---

## 7. 部署拓扑

### 7.1 Docker Compose 服务

| 服务 | 容器名 | 宿主机端口 |
|------|--------|------------|
| mysql | course-mysql | 无（仅容器内 3306） |
| redis | course-redis | 无（仅容器内 6379，需密码） |
| elasticsearch | course-elasticsearch | 无（仅容器内 9200） |
| backend | course-backend | **8082** |
| frontend | course-frontend | **8888** |

网络：外部网络 `local_net`（需预先 `docker network create local_net`）。

### 7.2 公网访问

- 域名：`course.example.com`
- VPS Nginx 配置参考：`deploy/vps-nginx-course.conf`
- 本机 Nginx 参考：`deploy/local-nginx-8888.conf`
- 监控：`deploy/UPTIME_KUMA_SETUP.md`

---

## 8. 设计原则

1. **统一响应**：`Result<T>` / `ResultCode`
2. **分层依赖单向**：Web → Application → Infrastructure → Domain → Common
3. **安全默认**：JWT + 可配置白名单；生产关闭 API 文档
4. **可观测**：`/api/health`、`/api/ping`、容器 healthcheck、操作日志
5. **可扩展对接**：`ApiProvider` + Docking 批量同步 + External API

---

## 9. 历史说明

早期文档描述的是单模块 `backend/src/main/java` 结构。当前以多模块为准；单模块源码目录仅作历史残留，不参与 Docker 构建。
