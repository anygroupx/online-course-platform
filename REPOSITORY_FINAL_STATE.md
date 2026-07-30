# 仓库状态说明

> 更新时间：2026-07-12

本仓库是 `online-course-platform` 的**完整自包含代码快照**。

## 当前结构

- `backend/`：Spring Boot **多模块**工程（直接纳入本仓，非 submodule）
  - 模块：`course-common` / `course-domain` / `course-infrastructure` / `course-application` / `course-web`
  - 版本：`2.0.1`
- `frontend/`：Vue 3 前端（直接纳入本仓）
  - 版本：`1.1.2`
- `database/`：`schema.sql` + `migrations/`
- `deploy/`、`docker-compose.yml`：部署资产

Git Submodule 依赖已移除，克隆本仓即可获得前后端完整代码。

## 历史残留（请勿当作现行入口）

| 路径 | 说明 |
|------|------|
| `backend/src/` | 旧单模块源码残留 |
| `backend/bin/` | 旧构建/文档残留 |
| `docs/archive/` | 阶段性交付文档归档 |
| 文档中的 8080 生产端口描述 | 已废弃；容器生产为 **8082** |

## 后续开发

建议在 `anygroupx` 组织继续迭代：

- 主仓：https://github.com/anygroupx/online-course-platform
- 后端：https://github.com/anygroupx/backend
- 前端：https://github.com/anygroupx/frontend

文档入口：[docs/README.md](./docs/README.md)

## 安全文档

- [docs/SECURITY_FIXES.md](./docs/SECURITY_FIXES.md)
- [docs/SECURITY_ROADMAP.md](./docs/SECURITY_ROADMAP.md)
