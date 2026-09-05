# API Provider 动态出站策略与启用流程

更新时间：2026-09-06。

## 管理员使用流程

1. 新建接口，填写类型、API 基础地址和凭据，保存后为 **待验证**（`status=2`）。
2. 点击 **测试连接**。测试只读取余额或商品，不下单、不修改第三方数据，也不自动启用。
3. 测试通过后点击 **启用**。普通余额、商品、查课、下单、日志及批量同步只使用已启用接口。
4. 修改地址、类型或凭据后，旧验证失效，重新执行测试和启用。已禁用的接口保持禁用。
5. **禁用**不做 DNS 或 HTTP 请求，第三方故障时也能立即操作。

测试使用已保存的配置。编辑时密码、API Key、Token、Cookie 留空表示保留，接口响应不返回这些字段；用户名按既有脱敏规则显示。验证时间、操作人、版本号和健康检查字段由服务端维护，不接受表单伪造。

`config_version` 用于比较并更新：测试过程中编辑、禁用或删除接口，旧测试结果不能授权新配置。并发冲突会提示刷新后重试。

## URL 约定

`api_url` 为基础地址，可带固定目录，不能包含操作参数：

```text
https://provider.example.com
https://provider.example.com/openapi
```

- 协议和域名转小写，国际化域名转 ASCII；移除默认端口、末尾斜线，规范化点路径。
- 拒绝 userinfo、query、fragment、IP 字面量以及有歧义的编码和 authority。
- Daytime / `29` 同时接受基础地址与末尾 `/api.php`，统一保存为基础地址，避免重复拼接。
- 默认 HTTPS / 443。HTTP 和非默认端口仍需部署级显式例外，管理员表单无法自行放宽。

## 出站安全边界

动态 Provider 不再使用 `OUTBOUND_PROVIDER_ALLOWED_HOSTS`。一个 Provider 的已保存配置只授权其精确协议、域名和端口，不生成跨 Provider 的共享白名单。

保存、启用、连接测试和运行时请求继续执行 `SsrfGuard` 校验。每次连接检查全部 DNS 地址并固定本次连接 IP，保留私网/回环/链路本地/保留地址拦截、TLS 校验、禁止重定向、超时和响应大小上限。

Turnstile、AQKS、安全告警 Webhook 等固定系统集成仍使用各自的静态策略，未扩大权限。独立 Egress Proxy / 容器防火墙属于可选纵深防御，本次没有部署。

### 环境变量

| 配置 | 默认值 / 作用 |
|------|---------------|
| `OUTBOUND_PROVIDER_ALLOWED_HOSTS` | 不再读取，可从旧部署配置移除；新增普通 HTTPS Provider 无需改环境变量或重启 |
| `OUTBOUND_PROVIDER_HTTP_ALLOWED_HOSTS` | 空；按精确 ASCII 域名允许 HTTP，不支持 URL 或通配符 |
| `OUTBOUND_PROVIDER_ALLOWED_PORTS` | 空；允许使用的额外端口。每个请求仍仅授权该 Provider 自己的端口 |
| `OUTBOUND_DNS_MODE` | `system`；Fake-IP 网络可用 `cloudflare-doh`，仍检查真实解析结果，不降级绕过 |
| `PROVIDER_HEALTH_ENABLED` | `false`；可选的已启用接口只读健康检查 |
| `PROVIDER_HEALTH_INTERVAL_MILLIS` | `1800000`；检查调度间隔，默认 30 分钟 |

修改部署级例外或健康检查开关需要重建/重启后端容器；这与新增普通 Provider 无需重启是两回事。

健康检查使用独立单线程、分页扫描和防重叠保护，不占用订单/支付调度线程，不会自动启用、停用、下单或生成管理员验证记录。健康状态变更时才记录探测日志。

## 管理端 API

下列路径含应用前缀 `/api`，要求已认证的管理员及 `api-provider:update` 权限：

| 方法 | 路径 | 行为 |
|------|------|------|
| POST | `/api/admin/api-providers` | 保存新接口，不能直接以 `status=1` 绕过验证 |
| PUT | `/api/admin/api-providers` | 更新配置；敏感配置变更清除验证 |
| POST | `/api/admin/api-providers/{id}/test-connection` | 无需请求体，测试已保存的配置 |
| PATCH | `/api/admin/api-providers/{id}/status` | `{"status":1}` 启用，`{"status":0}` 禁用 |
| POST | `/api/admin/api-providers/{id}/balance` | 仅已启用接口，刷新余额 |
| GET | `/api/admin/api-providers` | 脱敏列表，含验证及最近检查信息 |

测试成功返回规范化地址、域名、耗时、验证时间、验证人和当前状态。状态 `2` 在未验证时显示“待验证”，验证通过后显示“待启用”。

Provider 请求失败返回 HTTP 502。具有相应权限的管理端收到安全的 `data.reason` 和 `errorId`；普通用户仍只收到“第三方服务暂不可用”。分类包括 DNS、非公网地址、超时、TLS、HTTP 状态、重定向、响应过大、格式错误和上游拒绝。

错误中不包含解析出的具体 IP、凭据、请求参数、原始响应或异常堆栈。排查时用 `errorId` 对照服务端的 Provider ID、类型、操作、规范化域名、分类和耗时日志。

## 上线与数据库迁移

迁移文件：`database/migrations/017_provider_outbound_governance.sql`。

### 已有数据库

1. 备份数据库，核对既有迁移已执行（Provider 的 `last_sync_time` 来自 `001`），确认当前结构尚未包含本迁移新增的字段。此脚本是**一次性增量迁移，不可重复执行**。
2. 安排维护窗口，暂停应用写入；在部署新后端前执行迁移。
3. 更新前后端到同一版本，启动并验证功能。不要让旧前端绕过新启用流程。

在项目根目录执行（密码从容器已有环境读取，不写入示例或 shell 历史）：

```bash
# 先按运维备份流程完成备份，再执行一次迁移。
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot online_course' \
  < database/migrations/017_provider_outbound_governance.sql
```

迁移扩大 URL 和加密凭据列、增加配置版本/验证/检查字段，并将新记录默认状态改为 `2`。已有 `0` / `1` 状态保留，已有启用接口以历史管理员配置为授权，**不会伪造验证时间或操作人**；此后修改目标/凭据，或禁用后重新启用，需连接验证。无效及空状态置为禁用。

### 全新数据库

直接使用当前 `database/schema.sql`；已包含这些字段，不要再执行 `017`。示例 Provider 默认为待验证，不会因初始化数据自动发起请求。

### 验收与回退

- 新建一个 HTTPS Provider，无需加入任何全局 Provider 域名列表；验证“保存 → 待验证 → 测试 → 待启用 → 启用”。
- 修改 URL 或凭据后，验证旧的启用资格被撤销；断网时仍可禁用。
- 使用不可接受的地址、错误凭据和重定向响应检查分类及脱敏。
- 回退前先暂停应用并备份。新增列可保留，但旧版本不了解 `status=2` 的审批语义；应审查并保持待验证接口禁用，不要批量将其改为启用。勿直接删除验证列或缩短加密凭据列。

## 本地回归

```bash
cd backend
mvn -B test

cd ../frontend
npm ci
npm test
npm run build
# 首次使用需安装 Chromium；浏览器测试在本地拦截 API，不访问真实 Provider。
npx playwright install chromium
npm run test:browser
```

后端覆盖 URL/单目标策略、DNS/DoH/SSRF、TLS/超时/重定向、各 Provider 响应分类、管理员授权与脱敏、并发验证失效、健康任务隔离等。浏览器回归覆盖真实 Vue 管理页的保存/测试/启用/余额/改址/失败流程及移动端布局。
