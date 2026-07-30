# 安全加固变更说明（P0/P1）

> 更新时间：2026-07-12
> 已落地变更说明，与多模块代码及当前 compose 对齐。


> 基于审计报告对 `online-course-platform` 的安全整改记录。
> 代码主路径：`backend/course-web` + `course-application` + `course-domain` + `course-infrastructure` + `course-common`。

## 一、P0 已处理项

| 审计项 | 处理说明 |
|---|---|
| 轮换已提交密码/Token/API Key/支付凭据 | 仓库模板已清空为占位符（`.env` / `.env.example`）。**真实生产凭据必须由运维立即轮换**（数据库、JWT、Redis、支付宝、第三方 API）。 |
| 暂时关闭/限制公网访问 | 建议维护窗口关闭公网入口；Nginx 已加 HSTS/CSP 等安全头。数据端口已不再映射宿主机。 |
| 公告控制器固定返回管理员 ID | `AnnouncementController` 改为 `SecurityUtils.getCurrentUserId()`，管理接口 `@PreAuthorize("hasRole('ADMIN')")`。 |
| AQKS / 客服 / 支付配置真实授权 | AQKS、支付配置、管理订单、API Provider、系统配置等加 `@PreAuthorize` / `SecurityUtils.requireAdmin()`；客服会话归属校验 + 管理接口角色限制。 |
| 下线学生明文密码导出 | `OrderExportServiceImpl` 不再导出明文密码，仅导出“已设置/未设置”。 |
| 停止 API 返回数据库实体 | `User`/`CourseOrder`/`ApiProvider`/`PaymentConfig`/`AdminOrder` 控制器改为 VO + `SensitiveDataMasker`；实体敏感字段 `@JsonIgnore`。 |
| 支付宝回调与主动同步重复入账 | `markPaidIfPending` 条件更新 + `payment_event` 审计幂等 + `account_ledger` 唯一键入账；并发场景下 PAID 后账本补齐，避免“已支付未入账”。 |
| 关闭 MySQL/Redis/ES 宿主机公开端口 | `docker-compose.yml` 改为 `expose`，取消 `13306/6379/9200/9300` 宿主机映射；Redis 启用密码。 |
| 删除生产 CORS `*` | 生产 CORS 过滤 `*`，强制显式域名列表。 |
| 禁用默认管理员密码并强制首次改密 | 登录检测默认密码设置 `mustChangePassword`；`MustChangePasswordFilter` 拦截业务接口；改密需强度校验并清除标志。 |

## 二、P1 已处理项

| 审计项 | 处理说明 |
|---|---|
| 轻量 RBAC | `User.role` = `ADMIN/CS/USER` + JWT authorities + `@PreAuthorize`。 |
| 资金流水与余额原子更新 | `AccountLedgerServiceImpl` + `UserMapper.increaseBalance/decreaseBalance`；订单/充值/开户/API 开通走账本。 |
| Refresh Token 哈希与撤销 | 仅存 `token_hash`，登出/重放撤销 family。 |
| API Key 哈希/作用域/过期 | 落库 `api_key_hash/prefix/scopes/expire`；创建仅返回一次明文；External API 校验哈希（兼容旧明文并升级）。 |
| DTO/VO 白名单响应 | `UserVO`/`CourseOrderVO`/`ApiProviderVO`/`PaymentConfigVO`。 |
| 第三方密码/支付私钥加密 | `SecretCrypto` AES-GCM；`PaymentConfigServiceImpl`/`ApiProviderServiceImpl` 写入加密、运行解密。生产建议替换为 KMS。 |
| 登录/注册/外部 API/支付限流 | `RateLimitFilter` + 进程内滑动窗口。 |
| 客服会话所有权校验 | 会话归属 + senderType 服务端决定。 |
| 正确 HTTP 状态码与错误 ID | `GlobalExceptionHandler` 返回 401/403/404/409/422/429/500，并带 `errorId`。 |
| 清理 Git 历史敏感内容 | **未自动 force-push**。请使用 `gitleaks` + `git filter-repo` 后人工审核再推送。 |

## 三、运维必须立即执行的轮换清单

1. **数据库**：重置 MySQL root/`course_user` 密码，更新部署环境变量。
2. **JWT_SECRET**：生成 ≥32 字节随机串，滚动后全员重新登录。
3. **Redis 密码**：设置 `REDIS_PASSWORD`。
4. **APP_CRYPTO_SECRET**：设置后重新录入支付私钥/第三方密钥（旧明文可在写入时加密）。
5. **支付宝/第三方 API 密钥**：在对应控制台轮换，并在后台重新配置。
6. **默认管理员**：使用强密码登录后立即修改；确认 `must_change_password=0`。
7. **执行迁移**：`database/migrations/007_security_hardening.sql`。
8. **公网暴露面**：确认 VPS 防火墙仅开放 80/443；后端/DB/Redis/ES 不对公网。

## 四、验证建议

```bash
cd backend
mvn -pl course-web -am -DskipTests compile
```

部署后检查：
1. 非管理员访问 `/admin/**` 应 403
2. 订单导出不包含明文密码
3. 用户/订单接口响应无 password/apiKey/privateKey
4. 默认密码登录后除改密外业务接口 403
5. 支付回调重复通知不重复加余额

## 五、仍属 P2 / 后续

见 `docs/SECURITY_ROADMAP.md`。


## 六、本次续修（编译与残余）

1. 修复 `AnnouncementController` 错误调用 `getPublishedAnnouncements`，恢复前端使用的 `/latest` 与 `/system` 接口。
2. `AdminOrderController` 订单列表/详情/倒计时接口改为 `CourseOrderVO`，避免管理端实体直出。
3. 修复支付入账竞态：订单已被标记 PAID 时仍执行账本幂等补齐，事件表失败不再阻断入账。
4. `JwtUtil` 恢复密钥 fail-fast（空密钥或 <32 字节拒绝；生产拒绝 placeholder）。
5. `mvn -pl course-web -am -DskipTests compile` 已通过。

---

## P2 安全建设（2026-07-12）

### 已实现
1. **管理员 MFA（TOTP）**
   - 表字段：`sys_user.mfa_*`、`mfa_challenge`
   - 接口：`/auth/mfa/status|setup|setup/confirm|disable|verify`
   - 登录：管理员启用 MFA 后仅返回 `mfaChallengeId`，验证通过再签发 Token
   - 备用码：beginSetup 展示一次，confirm 按同一批哈希入库
2. **集中式安全审计 + 告警**
   - 表：`security_audit_log`
   - 埋点：登录失败/成功、访问拒绝、支付回调异常、API Key 变更、MFA 事件、对账
   - `SecurityAlertNotifier` 支持 Webhook
3. **支付日终对账**
   - 表：`payment_reconcile_report`
   - 任务：`PaymentReconcileTask`（默认每天 01:15）
   - 管理端：`/admin/security/reconcile**`
4. **安全响应头**
   - `SecurityHeadersFilter`：CSP / HSTS / X-Content-Type-Options / Referrer-Policy 等
5. **前端 MFA 登录二次验证**
   - `Login.vue` + `stores/user.js` + `api/auth.js`
6. **工程化**
   - CI：`.github/workflows/security-scan.yml`
   - 本地脚本：`scripts/security/*`
   - 文档：`docs/security/*`、ASVS 清单
7. **测试**
   - `AuthServiceImplTest` MFA 分支
   - `BolaBflaAuthorizationTest`
   - `TotpUtilTest`

### 迁移
- `database/migrations/008_p2_security.sql`

### 配置
```yaml
app:
  security:
    alert-webhook: ${SECURITY_ALERT_WEBHOOK:}
    mfa-issuer: ${MFA_ISSUER:OnlineCoursePlatform}
    reconcile-enabled: true
    reconcile-cron: "0 15 1 * * ?"
```

### 验证
```bash
cd backend
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn -pl course-web -am -DskipTests compile
mvn -pl course-web -am test \
  -Dtest=AuthServiceImplTest,BolaBflaAuthorizationTest,TotpUtilTest \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
```
