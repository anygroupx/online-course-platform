# 在线网课平台

> 基于 Spring Boot 3 多模块 + Vue 3 的前后端分离在线网课管理平台

| 项目 | 说明 |
|------|------|
| 后端版本 | `2.0.1` |
| 前端版本 | `1.1.2` |
| 生产域名 | `https://course.example.com` |
| 仓库 | [anygroupx/online-course-platform](https://github.com/anygroupx/online-course-platform) |

## 项目简介

现代化的在线网课管理平台，支持课程查询、订单管理、代理体系、第三方平台对接、支付充值、AQKS 自营刷课、倒计时任务等能力。采用前后端分离与后端多模块（DDD 风格）架构。

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot **3.2.1**、Spring Security + JWT、MyBatis Plus **3.5.7**、Knife4j **4.4.0** |
| 前端 | Vue **3.4**、Vite **5**、Element Plus **2.5**、Pinia、Vue Router **4**、Axios |
| 数据 | MySQL **8.0**、Redis **7**（可选）、Elasticsearch **8.11**（操作日志检索） |
| 部署 | Docker Compose、Nginx、WireGuard + VPS 反向代理 |

## 快速开始

### 方式一：Docker 一键部署（推荐）

```bash
# 1. 环境变量（必须设置强密码，无弱默认）
cp .env.example .env
# 必填：MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD / JWT_SECRET / REDIS_PASSWORD

# 2. 外部网络（仅首次）
docker network create local_net

# 3. 部署
chmod +x deploy.sh && ./deploy.sh
# Windows: deploy.bat
```

| 服务 | 本地地址 |
|------|----------|
| 前端 | http://localhost:8888 |
| 后端 API | http://localhost:8082/api |
| 健康检查 | http://localhost:8082/api/health |
| MySQL / Redis / ES | **仅 Docker 内网**（不映射宿主机端口） |

详细说明见 [DOCKER_DEPLOY.md](./DOCKER_DEPLOY.md)、[QUICKSTART.md](./QUICKSTART.md)。

### 方式二：本地开发

**环境要求**：JDK 17+、Node.js 18+、Maven 3.8+、MySQL 8.0+

```bash
# 数据库
mysql -u root -p -e "CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p online_course < database/schema.sql
# 可选：按需执行 database/migrations/*.sql

# 后端（多模块，启动模块为 course-web）
cd backend
# 先按需修改 course-web/src/main/resources/application.yml 中的数据源
mvn clean install -DskipTests
mvn -pl course-web -am spring-boot:run
# 开发默认端口 8080，上下文 /api

# 前端
cd frontend
npm install
npm run dev
# 默认 http://localhost:5173
```

### 默认账号

- 用户名：`admin`
- 密码：`123456`（生产环境务必修改）

## 项目结构

```
online-course-platform/
├── backend/                         # 后端（Maven 多模块，版本 2.0.1）
│   ├── course-common/               # 公共常量、统一响应、工具
│   ├── course-domain/               # 实体、DTO、VO、领域对象
│   ├── course-infrastructure/       # Mapper、外部 API、缓存、搜索
│   ├── course-application/          # 应用服务（业务编排）
│   ├── course-web/                  # Controller、Security、启动入口
│   ├── config/application-prod.yml  # 生产配置挂载
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                        # 前端（Vue 3 + Vite，版本 1.1.2）
│   ├── src/api/                     # 接口封装
│   ├── src/components/              # 公共组件（含企业级表格）
│   ├── src/composables/             # 组合式逻辑
│   ├── src/layouts/                 # 布局
│   ├── src/router/                  # 路由
│   ├── src/stores/                  # Pinia
│   ├── src/views/                   # 页面
│   ├── nginx.conf
│   └── Dockerfile
├── database/
│   ├── schema.sql                   # 初始化结构
│   ├── test_data.sql
│   └── migrations/                  # 增量迁移 001~007
├── deploy/                          # Nginx / 监控配置
├── docs/                            # 项目文档（见 docs/README.md）
├── docker-compose.yml
├── deploy.sh / deploy.bat
└── .env.example
```

> 说明：`backend/src`、`backend/bin` 为历史遗留目录，**实际运行与 Docker 构建以多模块（`course-*`）为准**。

## 核心功能

### 用户与代理
- JWT 登录 / 登出 / Token 刷新
- 邀请码注册、多级代理、费率管理
- 余额充值（卡密 / 支付宝）、密码修改
- API Key 开通与外部接口鉴权
- RBAC 角色（`ADMIN` / `CS` / `USER`，见迁移 007）

### 订单与查课
- 单笔 / 批量下单、进度同步、补单（限次）
- 查课对接第三方平台
- 管理员全量订单管理、批量操作、导出
- 倒计时任务 / 考试倒计时 / 历史记录
- AQKS 自营刷课（启停、加时、考试状态）

### 课程与对接
- 课程平台 CRUD、分类管理
- 第三方 API 提供商管理
- 一键导入平台、批量进度同步（Benz 等）

### 运营与系统
- 系统配置 / 系统变量
- 公告、客服会话
- 操作日志（含 ES 检索能力）
- 统计分析
- 主题配置

### 支付
- 支付宝创建订单、异步通知、同步回调
- 支付配置管理、支付订单查询

## 端口与环境对照

| 环境 | 前端 | 后端 | context-path |
|------|------|------|--------------|
| 本地开发 | 5173 | **8080** | `/api` |
| Docker / 生产容器 | 8888→80 | **8082** | `/api` |
| 公网 | https://course.example.com | https://course.example.com/api | `/api` |

API 文档（开发环境）：`http://localhost:8080/api/doc.html`
生产默认关闭 Knife4j（`API_DOC_ENABLED=false`）。

## 文档导航

| 文档 | 说明 |
|------|------|
| [docs/README.md](./docs/README.md) | 文档索引 |
| [QUICKSTART.md](./QUICKSTART.md) | Docker 快速上手 |
| [docs/QUICK_START.md](./docs/QUICK_START.md) | 本地开发快速开始 |
| [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) | 架构设计 |
| [docs/API_DOCUMENTATION.md](./docs/API_DOCUMENTATION.md) | API 总览 |
| [docs/FINAL_FEATURE_LIST.md](./docs/FINAL_FEATURE_LIST.md) | 功能清单 |
| [docs/DEPLOYMENT_GUIDE.md](./docs/DEPLOYMENT_GUIDE.md) | 部署指南 |
| [DOCKER_DEPLOY.md](./DOCKER_DEPLOY.md) | Docker 部署详解 |
| [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md) | 故障排查 |
| [docs/DATABASE.md](./docs/DATABASE.md) | 数据库与迁移 |
| [docs/SECURITY_FIXES.md](./docs/SECURITY_FIXES.md) | 安全加固说明 |
| [docs/archive/](./docs/archive/) | 历史交付 / 阶段性文档归档 |

## 仓库说明

本仓库为完整单体仓库：`backend/` 与 `frontend/` 已直接纳入，不再使用 Git Submodule。

后续迭代在 `anygroupx` 组织下进行：

- 主仓：https://github.com/anygroupx/online-course-platform
- 后端：https://github.com/anygroupx/backend
- 前端：https://github.com/anygroupx/frontend

## License

仅供学习与内部使用。
