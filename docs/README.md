# 文档索引

> 最后更新：2026-07-12
> 对应代码：后端 `2.0.1` / 前端 `1.1.2`

## 入门

| 文档 | 说明 |
|------|------|
| [../README.md](../README.md) | 项目总览 |
| [../QUICKSTART.md](../QUICKSTART.md) | Docker 一分钟上手 |
| [QUICK_START.md](./QUICK_START.md) | 本地开发启动 |
| [FINAL_FEATURE_LIST.md](./FINAL_FEATURE_LIST.md) | 功能清单（与代码对齐） |

## 架构与数据

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 多模块架构、分层、部署拓扑 |
| [DATABASE.md](./DATABASE.md) | 表结构、迁移脚本 |

## 接口

| 文档 | 说明 |
|------|------|
| [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) | REST API 总览 |
| [ADMIN_ORDER_MANAGEMENT_API.md](./ADMIN_ORDER_MANAGEMENT_API.md) | 管理员订单 API |
| [ADMIN_ORDER_MANAGEMENT_GUIDE.md](./ADMIN_ORDER_MANAGEMENT_GUIDE.md) | 管理员订单使用指南 |
| [batch-sync-usage.md](./batch-sync-usage.md) | 批量同步对接用法 |

## 部署与运维

| 文档 | 说明 |
|------|------|
| [../DOCKER_DEPLOY.md](../DOCKER_DEPLOY.md) | Docker Compose 生产部署 |
| [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) | 本地 / 服务器部署总指南 |
| [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) | 常见问题 |
| [DOCKER_FIX_405_CORS.md](./DOCKER_FIX_405_CORS.md) | 405 / CORS 专项 |
| [../deploy/UPTIME_KUMA_SETUP.md](../deploy/UPTIME_KUMA_SETUP.md) | Uptime Kuma 监控项 |

## 安全

| 文档 | 说明 |
|------|------|
| [SECURITY_FIXES.md](./SECURITY_FIXES.md) | 已落地的 P0/P1 安全加固 |
| [SECURITY_ROADMAP.md](./SECURITY_ROADMAP.md) | P2+ 安全建设路线图 |

## 前端组件

| 文档 | 说明 |
|------|------|
| [../frontend/docs/EnterpriseTableSystem.md](../frontend/docs/EnterpriseTableSystem.md) | 企业级表格体系 |
| [../frontend/docs/EnterpriseTable.md](../frontend/docs/EnterpriseTable.md) | EnterpriseTable 组件 |
| [../frontend/docs/EnterpriseFilter.md](../frontend/docs/EnterpriseFilter.md) | EnterpriseFilter 组件 |

## 历史归档

阶段性交付说明、功能对比、实现纪要等已移至 [archive/](./archive/)，仅作历史参考，**不代表当前实现**。

## 文档维护约定

1. 端口：开发后端 **8080**，Docker/生产容器 **8082**，统一 `context-path=/api`；MySQL/Redis/ES 在 Compose 中不映射宿主机端口。
2. 后端结构以 `backend/course-*` 多模块为准，勿再描述为单模块 `backend/src/main/java` 结构。
3. 新增接口或页面后，优先更新 `API_DOCUMENTATION.md` 与 `FINAL_FEATURE_LIST.md`。
4. 数据库变更放入 `database/migrations/`，并在 `DATABASE.md` 登记。
