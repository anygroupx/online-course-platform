# 数据库与迁移

> 更新时间：2026-09-06
> 数据库名：`online_course`
> 字符集：`utf8mb4` / `utf8mb4_unicode_ci`

## 初始化

```bash
mysql -u root -p -e "CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p online_course < database/schema.sql

# 可选测试数据
mysql -u root -p online_course < database/test_data.sql

# 已有数据库升级：核对已执行的迁移，只运行尚未应用的脚本。
# 不要在最新 schema.sql 初始化后无差别重跑全部迁移。
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
| `017_provider_outbound_governance.sql` | Provider 待验证状态、配置版本、验证人/时间、健康分类；扩大 URL/加密凭据列 |

### 017 Provider 出站治理

已有数据库须在新后端上线前执行一次；最新 `schema.sql` 已包含该结构，不能重复执行 `017`。保留已有启用/禁用状态；不为历史记录伪造验证信息。新 Provider 默认为待验证，配置敏感字段变化后重新验证。

操作与回退注意事项见 [Provider 出站治理指南](./PROVIDER_OUTBOUND_GOVERNANCE.md#上线与数据库迁移)。

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


### 黑鲨双履约模式与资格材料（migrations 034–035）

服务商城继续使用 `service_product → service_order_operation → service_order`；不使用 `course_order` 或 AQKS 自动任务。
`fulfillment_mode` 支持 `UPSTREAM`（默认）和 `SELF_OPERATED`。仅黑鲨商品允许自营，订单在创建时复制模式，后续商品修改不会改变历史订单。

- `UPSTREAM`：原有交易流程保持不变，调用黑鲨 CREATE 并绑定远端订单，使用 SYNC 更新状态；SKU 3/4 仍使用现有官方人脸授权流程。
- `SELF_OPERATED`：仅适用于黑鲨 SKU 1–4，`provider_id` 必须为 NULL，不加载接口配置、不读取目录，也不调用查询、预检、账号会话、下单或同步接口。报价按商品销售价计算；确认在一个事务中扣账、创建 PENDING 订单及加密履约资料。订单保存履约模式快照，商品后续变更不影响历史订单。

`service_order_fulfillment` 以 `order_id` 为唯一主键，保存 `payload_encrypted`、版本及时间；使用 `SecretCrypto` / `APP_CRYPTO_SECRET`。只保留账号、密码和所选计划/区域/时间/规则等必要字段，不保留远端预检或人脸 token。普通订单和审计 DTO 不含这些资料。

SKU 3/4 使用独立的人脸资格材料授权。图片经安全解码、尺寸/像素限制和元数据剥离后加密暂存；草稿 15 分钟过期，并在确认订单或补资料时原子消费。订单资产保存 MIME、尺寸、字节数和 SHA-256；加密内容由 `APP_CRYPTO_SECRET` 保护。资格核验及履约期间保留，订单完成、取消或退款后默认再保留 24 小时，可通过 `NATIVE_SERVICE_FULFILLMENT_ASSET_RETENTION_HOURS` 配置终态后的保留时间。

`service-order:fulfill` 与 `service-order:biometric` 默认仅授予 SUPER_ADMIN、OPERATOR。普通履约资料读取要求 `service-order:fulfill`；人脸图片读取额外要求 `service-order:biometric`，使用独立限流。每次资料或人脸读取须先成功写入只含标识符的安全审计记录；审计存储失败时拒绝返回内容。

SKU 3/4 初始下单与补资料均须显式授权。资格状态为 PENDING、VERIFIED、NEEDS_INFO、REJECTED；管理员可通过、要求补充或拒绝。用户在 NEEDS_INFO 时提交的资料加密保存并使状态回到 PENDING。只有 VERIFIED 才可 START。未开始订单可由管理员执行本地全额取消退款：只接受 PENDING 且完成数为 0 的订单，退款额由服务端账本计算，扣/退与订单操作原子提交且不得调用供应商或支付网关。REJECTED 不自动退款。

本地生命周期：START（PENDING → ACTIVE，须 VERIFIED）、PROGRESS（单调增加进度）、COMPLETE（完成全部数量）、ATTENTION / RESUME（必填备注）、CANCEL_REFUND（PENDING 且 completed=0）。每次变更记录版本化 LOCAL_* 操作；并发版本冲突和重复请求不得重复扣款或退款。

部署前须备份数据库，并在部署依赖新结构的后端之前由授权人员于 MySQL 8 显式执行 migration 035；它依赖 migrations 018–034。不要修改或重放历史 migration。新建库的 `database/schema.sql` 已包含截至 035 的服务商城结构。敏感资料不得写入普通 DTO、URL、日志或浏览器持久化；关闭窗口、切换订单/页面或权限变化时清除前端内存。


#### 035 升级前检查与资料清理

- 034 已创建的自营商品会移除原接口绑定，并递增商品版本，使旧金额预览失效。历史订单仅按自己的 `fulfillment_mode` 快照处理：自营订单及其操作的接口快照置空，历史 `UPSTREAM` 订单及其接口版本、身份快照不变；不会根据商品当前模式改写订单语义。
- 执行迁移前先检查旧自营 SKU 是否跨接口重复；有结果时必须先由业务管理员核对并明确处理商品，不得自动合并商品、删除订单或跳过唯一约束：

  ```sql
  SELECT provider_type, project, remote_product_id, COUNT(*) AS duplicate_count
  FROM service_product
  WHERE fulfillment_mode = 'SELF_OPERATED'
  GROUP BY provider_type, project, remote_product_id
  HAVING COUNT(*) > 1;
  ```

- MySQL DDL 不保证整份脚本事务回滚。先备份、停写并核对迁移前置条件；执行失败应检查并恢复，不要直接重放已执行部分。035 在移除旧绑定前先建立自营唯一索引，遇到重复数据会停止，不会擅自处理历史订单。
- 人脸 Draft 有效期为 15 分钟，确认订单时原子消费并清空 Draft 密文。独立的一分钟清理任务清空过期 Draft，以及已完成/取消/退款且超过保留期的订单图片；停用服务销售不会停用隐私资料清理。保留期由 `app.native-services.fulfillment-assets.retention-hours` 配置，默认 24 小时、最小 1 小时。
- 人工核验可记录计划、区域及单次公里范围，均保存在加密履约快照中，不修改已付款订单的次数、公里数、单价或实付金额。补资料会清除上次审核原因并回到 `PENDING`；账号密码、图片及授权凭据不得复制到操作备注。
- 本轮仅提供管理员取消未开始订单并全额退款；`ACTIVE` 部分退款不开放，也不开放用户自助退款。退款走原子账本入账，操作记录显示实际退款金额。

### RBAC 领域权限与外键迁移（036 / 037）

036 新增服务商品读写、服务订单读取/核对/退款、服务项目读写权限，并保留履约及人脸权限。采用固定角色策略（static-role-policy），仅重建上述九项领域权限的角色授予，不修改其他权限。SUPER_ADMIN 拥有全部权限；OPERATOR 获得运营七项权限，不含核对和退款；FINANCE 获得订单读取、核对、退款；AUDITOR 获得商品、订单、项目读取；CUSTOMER_SERVICE 与 USER 不授予上述管理权限。此策略尚不支持自定义角色编辑。

登录、刷新及 MFA 完成后的响应增加 `roles` 与 `permissions`。两者从数据库 Authority 并集投影，仅供前端 UX 使用。JWT 继续只证明身份，后端每次请求仍从数据库实时加载权限。旧 `role` / `isAdmin` 保留兼容，不应作为新授权依据。

角色分配事务先锁定 SUPER_ADMIN 角色行，再锁目标用户；角色变更审计必须成功写入，同事务失败时回滚。权限矩阵通过 `/admin/rbac/permissions`、`/admin/rbac/roles/{roleCode}/permissions`、`/admin/rbac/matrix` 只读展示，均要求 `rbac:manage`。

037 对四种关系进行孤儿数据预检；有孤儿数据时失败，不静默清理。通过后添加外键，级联仅删除映射，不删除用户。要求 MySQL >= 8.0.16，执行时暂停 RBAC 写入；DDL 不可事务回滚，部分失败后应检查状态，不可盲目重跑。

迁移验证：`python3 database/tests/rbac_mysql.py` 创建并销毁独立 MySQL 8 容器，覆盖重复执行、默认六角色矩阵、敏感权限收口、全新 schema 一致性、孤儿预检及映射级联，不连接生产数据库。**036 与应用权限链切换须协调发布，037 须在备份与数据预检后执行；所有生产发布和迁移仍需明确授权。**
