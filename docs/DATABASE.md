# 数据库与迁移

> 更新时间：2026-07-12
> 数据库名：`online_course`
> 字符集：`utf8mb4` / `utf8mb4_unicode_ci`

## 初始化

```bash
mysql -u root -p -e "CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p online_course < database/schema.sql

# 可选测试数据
mysql -u root -p online_course < database/test_data.sql

# 增量迁移（建议按序号执行）
for f in database/migrations/*.sql; do
  echo "Applying $f"
  mysql -u root -p online_course < "$f"
done
```

Docker 首次启动时，`schema.sql` 会挂载到 MySQL 初始化目录：

```yaml
./database/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
```

**注意**：Compose 初始化只会在数据卷为空时执行；已有数据卷需手动跑 migrations。

---

## 基线表（schema.sql）

| 表名 | 说明 |
|------|------|
| `sys_user` | 用户、代理关系、余额、API Key、状态 |
| `course_platform` | 课程/平台商品 |
| `api_provider` | 第三方接口提供商 |
| `course_order` | 课程订单 |
| `operation_log` | 操作日志 |
| `system_config` | 系统配置键值 |
| `recharge_record` | 充值记录 |
| `recharge_card` | 充值卡密 |
| `announcement` | 公告 |
| `customer_service_session` | 客服会话 |
| `customer_service_message` | 客服消息 |
| `system_variable` | 系统变量 |
| `countdown_config` | 倒计时配置 |
| `countdown_history` | 倒计时历史 |

> `platform_category`、支付相关表等可能在 schema 后续段落或迁移中补充，以实际 SQL 文件为准。

---

## 增量迁移（database/migrations）

| 文件 | 说明 |
|------|------|
| `001_add_last_sync_time.sql` | `api_provider.last_sync_time` 批量同步时间戳 |
| `002_add_category_support.sql` | 分类支持相关 |
| `003_add_refresh_token.sql` | Refresh Token 表 |
| `004_payment_system.sql` | 支付订单 / 配置 / 通知日志等 |
| `005_remote_category_mapping.sql` | `platform_category` 远程分类映射 |
| `006_aqks_study_log.sql` | AQKS 刷课日志表 `aqks_study_log` |
| `007_security_hardening.sql` | RBAC 角色、Token 哈希、API Key 哈希、资金流水 `account_ledger` 等 |

### 007 安全加固要点

- `sys_user.role`：`ADMIN` / `CS` / `USER`
- `must_change_password` / `password_changed_at`
- `api_key_hash` / `api_key_prefix` / `api_key_scopes` / `api_key_expire_time`
- `refresh_token` 增加 hash、family、撤销字段
- 不可变账本 `account_ledger`

执行前请备份；若列已存在需跳过对应 `ALTER`。

---

## Docker 连接信息（默认）

| 项 | 值 |
|----|----|
| 宿主机端口 | **无映射**（安全加固后仅容器网络） |
| 容器内 | `course-mysql:3306` |
| 库名 | `online_course` |
| 应用用户 | `course_user`（见 compose / `.env`） |
| root 密码 | `.env` 中 `MYSQL_ROOT_PASSWORD`（必填） |

本地调试进库：

```bash
docker exec -it course-mysql mysql -uroot -p online_course
```

---

## 开发数据源

开发配置位于：

`backend/course-web/src/main/resources/application.yml`

请按本机 MySQL 地址修改 `spring.datasource.*`，勿将生产密码提交到仓库。

生产 / Docker 优先使用环境变量：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
