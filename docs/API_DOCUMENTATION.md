# API 接口文档

> 更新时间：2026-09-12（与当前接口代码对齐；模拟回归不等于真实业务协议验收）
> Base Path：`/api`
> 开发：`http://localhost:8080/api`
> Docker/生产容器：`http://localhost:8082/api`
> 公网：`https://course.example.com/api`
> OpenAPI UI（开发）：`/doc.html`（Knife4j，生产默认关闭）

## 通用约定

### 响应结构

```json
{
  "code": 1,
  "message": "操作成功",
  "data": {}
}
```

> 统一成功码为 `code: 1`；失败需同时检查 HTTP 状态、`code`、`message`，并记录 `errorId`（如有）。

### 鉴权

| 类型 | 说明 |
|------|------|
| JWT | Header：`Authorization: Bearer <access_token>` |
| API Key | `/external/**` 使用 UUID `uid` + `api_key`（兼容 `key`）表单体认证，不需要 JWT |
| 匿名入口 | 仅 `/auth/login`、`/auth/refresh`、`/auth/mfa/verify`、`/register`、`/register/validate-invite-code`、`/client/bootstrap`、健康/支付回调/只读主题/文档等明确路径；`auth/current` 和 `auth/logout` 必须认证 |

### 客户端启动与会话

`GET /client/bootstrap` 匿名返回固定白名单：`branding={siteName,siteKeywords,siteDescription}` 与 `session={autoRefreshEnabled}`，不会透传系统配置实体。`GET /system/config` 要求 `system-config:read`，更新和重置要求 `system-config:update`；普通用户不再读取管理端配置。

登录、MFA完成与续期返回公开账号标识 `uid`，不返回数据库数字 `userId`。客户端按公开标识识别当前账号；同一 `sid` 会话族的正常令牌轮换保留订单筛选、表单和原确认编号，退出登录、账号/权限或会话族变化则清理私有状态。确认响应丢失仍只查询原操作，不重发交易。

### 角色

- `ADMIN`：管理员接口（`/admin/**` 等）
- `CS`：客服相关能力（视具体接口校验）
- `USER`：普通用户 / 代理

---

> 第三方七个业务接口、在线测试与安全兼容性详见 [EXTERNAL_API_GUIDE.md](./EXTERNAL_API_GUIDE.md)。

## 1. 健康检查

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/health` | 健康状态 `status=UP` | 否 |
| GET | `/ping` | 返回 `pong` | 否 |

---

## 2. 认证 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录，返回 token |
| POST | `/auth/logout` | 登出 |
| GET | `/auth/current` | 当前用户信息 |
| POST | `/auth/refresh` | 刷新 Access Token |

### 登录示例

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

---

## 3. 注册 `/register`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/register` | 邀请码注册 |
| GET/POST | `/register/validate-invite-code` | 校验邀请码（以实现为准） |

---

## 4. 用户

| 模块 | 典型路径 | 说明 |
|------|----------|------|
| 用户管理 | `/users/**` | 管理员用户 CRUD、费率、余额等 |
| 个人信息 | UserInfo 相关接口 | 资料、改密 |
| API Key | `POST /api-keys/enable` | 开通 API 密钥，明文仅返回一次 |
| API Key 轮换 | `POST /api-keys/rotate` | JWT + 当前密码验证，免费轮换自己的密钥，旧密钥立即失效 |

前端封装：`frontend/src/api/user.js`、`auth.js`

---

## 5. 订单（用户侧）

| 方法 | 路径前缀 | 说明 |
|------|----------|------|
| * | `/orders` | 创建、查询、取消、补单、详情等 |
| * | `/orders/batch` | 批量下单 |
| GET | `/orders/{orderNo}/progress-logs` | 按订单读取权限查询本地执行记录；不触发远程查询 |

前端封装：`frontend/src/api/order.js`

执行记录只返回 `id/progress/orderStatus/remarks/source/createTime`，按时间和ID倒序；仅在进度、状态或备注改变时追加，倒计时订单返回空列表。依赖迁移032；不返回账号密码或接口配置。课程ID是原样保存的字符串，长度上限2048字符，依赖迁移031。

---

## 6. 管理员订单 `/admin/orders`

完整说明见 [ADMIN_ORDER_MANAGEMENT_API.md](./ADMIN_ORDER_MANAGEMENT_API.md)。

常用接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/orders/query-all` | 全量查询 |
| GET | `/admin/orders/statistics` | 统计 |
| GET | `/admin/orders/{id}/detail` | 详情 |
| POST | `/admin/orders/{id}/force-update-status` | 强制改状态 |
| POST | `/admin/orders/{id}/force-update-dock-status` | 强制改对接状态 |
| POST | `/admin/orders/{id}/add-remark` | 备注 |
| DELETE | `/admin/orders/{id}` | 删除 |
| DELETE | `/admin/orders/batch-delete` | 批量删除 |
| POST | `/admin/orders/batch-operation` | 批量操作 |
| POST | `/admin/orders/export` | 导出 |
| POST | `/admin/orders/{id}/adjust-countdown` | 调整倒计时 |
| GET | `/admin/orders/countdown` | 倒计时订单列表 |
| GET | `/admin/orders/countdown-history*` | 倒计时历史 |
| POST | `/admin/orders/{id}/complete` | 手动完成 |
| * | `/admin/orders/*exam-countdown*` | 考试倒计时相关 |
| * | `/admin/orders/batch` | 管理员批量（OrderBatchController） |

---

## 7. 查课与课程平台

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/courses/query` | 查课 |
| * | `/courses` | 用户可见课程/平台列表 |
| * | `/admin/platforms` | 平台 CRUD（管理） |
| * | `/admin/platform-categories` | 分类管理 |

前端：`frontend/src/api/course.js`

---

## 8. 第三方对接

| 方法 | 路径 | 说明 |
|------|------|------|
| CRUD | `/admin/api-providers` | API 提供商配置；新建/敏感修改后待验证 |
| POST | `/admin/api-providers/{id}/test-connection` | 只读连接测试，记录验证人/时间，不自动启用 |
| PATCH | `/admin/api-providers/{id}/status` | `status=1` 启用（须验证通过）；`status=0` 禁用 |
| POST | `/admin/api-providers/{id}/balance` | 已启用接口余额刷新 |
| POST | `/admin/docking/import-platforms` | 一键导入平台 |
| POST | `/admin/docking/batch-sync` | 批量同步订单进度 |

详见 [batch-sync-usage.md](./batch-sync-usage.md)。

Provider 配置、单目标出站安全、错误分类及迁移详见 [PROVIDER_OUTBOUND_GOVERNANCE.md](./PROVIDER_OUTBOUND_GOVERNANCE.md)。

参数说明：

- `import-platforms`：`apiProviderId`、`priceMultiplier`、`targetCategoryId`
- `batch-sync`：`apiProviderId`、`timestampSeconds`、`offset`

> 请求需带 JWT，完整 URL 含 `/api` 前缀。

---

### 8.1 Benz 现有价格/说明更新（代码已部署，默认关闭）

与导入功能分离，需要 `platform:update`、迁移024及 `NATIVE_SERVICES_ENABLED=true`；只支持已启用的 `providerType=27`。响应 `Cache-Control: no-store`，批次仅创建人可访问；不发送上游写请求。

| 方法 | 路径 | 请求 / 行为 |
|------|------|------|
| POST | `/admin/platforms/price-refreshes` | 只读获取目录并保存预览，不更新课程 |
| GET | `/admin/platforms/price-refreshes/{id}` | 查询原批次及 READY/APPLIED/STALE/EXPIRED，不重新查上游 |
| POST | `/admin/platforms/price-refreshes/{id}/confirm` | `{consent:true}`；按预览原子更新所有目标，只更新价格/说明 |

预览请求：`{providerId, scope:"ALL_EXISTING"|"SELECTED", multiplier:"1.2", categoryId:null, skipCategoryIds:[], productIds:[]}`。SELECTED 必须指定最多500个远程ID；ALL_EXISTING 不接收选中ID。倍率 0.0001…1000、至多四位小数；分类ID最长50字，排除最多100项。后端再次按分类/排除/已关联规则筛选，不能信任客户端价格。

返回 `View={id,providerId,state,expiresAt,appliedAt,plan,notice}`；`plan.rows` 含 `localId,remoteId,title,oldPrice,newPrice,oldDescription,newDescription,descriptionProvided,changed`，金额为字符串；另含 selectedMissing/notImported/excluded 计数，不新增/删除任何课程。预览最多500个本地行、五分钟有效；十进制四舍五入到分，原始价格异常、溢出、正金额不足一分或说明超500字时拒绝。

确认只用已展示的服务端快照，不获取新价格。供应商配置版本、全部本地价格/说明/接口绑定需仍匹配；否则整批停止。名称、分类、排序、状态与其他配置不会回写。缺失说明不变、明确空串清空。数据库失败全部回滚；同一批次可安全重复确认但客户端不自动重试。响应丢失先 GET；再次确认只能由用户显式操作且沿用原ID。APPLIED 不再应用，即使后来课程价格已被其他人修改。功能关闭后仍允许读取本人结果。

---

### 8.2 Benz 执行编号查找与恢复（已实现，默认关闭）

用于已有可信回执但本地编号缺失的普通课程订单；不是重新下单、补单或批量自动找号。依赖迁移028、`NATIVE_SERVICES_ENABLED=true`，HTTP与服务层均要求当前有效管理员同时具有 `order:update` 和 `api-provider:update`，并重新核对数据库中的权限。所有响应 `Cache-Control: no-store`；关闭总开关后本节所有入口均拒绝访问。

路径前缀为 `/admin/orders/{orderId}/receipt-recoveries`，完整URL仍含 `/api`。

| 方法 | 路径 | 请求 / 行为 |
|------|------|------|
| POST | `/candidates` | 无业务请求体；事务外只读查单一次，仅返回本次响应内完整身份匹配、尚未占用的编号；不保存核对请求或关联编号 |
| POST | 前缀本身 | 保存READING请求后，事务外只读查单一次，验证指定编号和完整订单身份，保存预览；不关联编号 |
| GET | 前缀本身 | 仅当前操作者、当前订单最近20次核对记录；不调用供应商 |
| GET | `/{requestId}` | 查询原请求，并将过期READY/READING标为EXPIRED/INTERRUPTED；不重复查单 |
| POST | `/{requestId}/confirm` | `{consent:true}`；重新校验后只在本地原子关联编号和审计，不请求供应商 |

候选响应为 `Candidates={orderId,receiptIds,checkedAt,scope:"CURRENT_RESPONSE"}`，仅四个字段；`receiptIds` 最多20个唯一合法编号，`checkedAt` 为北京时间、无时区ISO格式。每次显式查找只读取一次既有 `chadan` 响应，不推断分页或完整订单总量。单次最多1000行；超过20个完整匹配候选时整次失败，不截断展示。`user/pass/kcname/cid` 缺失或不匹配的记录不列入；可选 `school/kcid` 如有提供也须一致。非法字段类型、非法或冲突编号、任意重复编号、重复JSON键、拼接JSON及异常成功码会拒绝整次读取，不显示为空结果。

查找前后以短事务复核权限、订单、商品、配置和同源配置列表摘要，HTTP不持有数据库事务；最终排除已绑定或已声明占用的编号。该入口不写订单、不创建核对请求或声明、不触发派发及资金操作；只有成功空响应才表示“本次未找到”，不能据此认定订单不存在。配置或订单并发变化按当前HTTP约定返回 **400、业务码 `-110`**，不是保证返回409。候选查找及预览共用每操作者5次/分钟的额度，不能切换入口增加额度；触限返回429及 `Retry-After`，限流服务不可用返回503并停止业务执行。

预览请求为 `{requestId,receiptId,evidence,ownershipConfirmed:true}`。`requestId` 是调用方生成的规范小写UUID；`receiptId` 匹配 `[A-Za-z0-9_-]{1,50}`；依据为10–1000字，去首尾空白后仍须至少10字，拒绝控制字符。不要填写密码或访问密钥；依据经过加密保存，不回传浏览器。同UUID只返回原请求，修改订单、编号或依据会拒绝；响应丢失先GET原请求，不自动重发POST。再次核对须由用户显式创建新UUID。

`View={id,orderId,orderNo,courseName,receiptId,state,expiresAt,appliedAt,notice}`。状态为 `READING/READY/APPLIED/READ_FAILED/CONFLICT/EXPIRED/INTERRUPTED`；时间为北京时间、无时区的ISO格式。预览从保存时起五分钟有效，等待数据库锁后再次核对期限。两次确认独立：预览需确认归属，最终关联另需 `consent:true`。候选查找与预览共享每操作者5次/分钟，记录查询和确认共享30次/分钟。

- 仅支持已启用且已核对的 `providerType=27` 配置。需原订单/商品/执行配置仍一致；未派发或正在提交、自营、归档、已记录编号、取消及退款订单均不能恢复。相同账号、密码、课程、商品存在多笔本地订单时拒绝猜测，包含历史归档订单。
- 使用既有 `chadan` 只读契约；返回的指定编号必须唯一，`user/pass/kcname/cid` 必须完整且与冻结快照精确一致。`cid` 是商品绑定，不是学生课程 `kcid`；可选学校/课程编号如有提供也不得矛盾。缺字段、重复或冲突编号、重复JSON键、拼接JSON、异常成功码均不能形成预览。查不到不表示订单不存在，真实响应兼容性仍待授权验收。
- 确认时重查当前权限、期限、订单和配置摘要以及编号归属。规范地址与执行账号相同的配置视作同一来源；唯一声明阻止同一编号或订单被重复关联。声明、条件更新和APPLIED审计同事务提交；并发冲突或失败回滚，不扣款、不退款、不改执行状态/进度。
- 查单不再采用第一条结果，必须匹配已知编号；缺编号时补单会拒绝，不再自动查出第一条后直接补单。下单响应只能接受唯一一致的结构化编号，不能从消息中的价格、数量等数字猜号。

真实订单详情提供“恢复执行编号”，页面打开不会自动查询。可主动查找并按原始回执选择候选，也可手填编号；不会默认选择第一条。选择只填编号，并清空旧核对依据与归属确认，仍须经过原五分钟预览和独立确认；手动改号也会撤销旧归属确认。支持查询时间/范围提示、空结果与失败区分、原请求及最近记录恢复、换订单/卸载丢弃旧响应、手机深色；POST不会因401自动重放。候选功能在2026-09-11候选查找批次新增、未部署，沿用028、不新增迁移；但整个新版后端仍须先完整应用030，不能以候选功能无新增迁移为由省略。业务默认关闭；自动缺号发现与批量分页的完整语义仍未完成。既有Benz查单/补单的安全修正不受总开关控制，相关代码发布或启用前需核查缺号存量并验证恢复契约。

历史候选查找本地验收（2026-09-11，不作为后续实习扩展的验收）：1431项后端、115项前端单元、23组实际Vue浏览器流程及前后端构建通过。浏览器首轮退出143后，以未变的冻结源码续跑余下7组，23组均已覆盖，不将原中断命令记为成功。测试使用模拟业务响应；未部署、未调用真实接口，本次无新增迁移且未重跑MySQL。详细证据及历史检查点见[开发验证](./PLUGIN_INTEGRATIONS.md#6-开发验证)。

---

## 9. AQKS 自营刷课 `/admin/aqks`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/aqks/start/{orderId}` | 开始 |
| POST | `/admin/aqks/stop/{orderId}` | 停止 |
| POST | `/admin/aqks/add-time/{orderId}` | 加时 |
| GET | `/admin/aqks/status/{orderId}` | 状态 |
| GET | `/admin/aqks/running/{orderId}` | 是否运行中 |
| POST | `/admin/aqks/running/batch` | 批量运行状态 |
| GET | `/admin/aqks/running-count` | 运行数量 |
| GET | `/admin/aqks/statistics` | 统计 |
| POST | `/admin/aqks/check-exam/{orderId}` | 检查考试 |
| POST | `/admin/aqks/sync-exam-status` | 同步考试状态 |

前端：`frontend/src/api/aqks.js`

---

## 10. 倒计时配置 `/admin/countdown-config`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/all` `/list` | 配置列表 |
| POST | `/update` `/batch-update` | 更新 |
| POST | `/reset` | 重置 |
| GET/POST | `/exam-configs` | 考试倒计时配置 |

前端：`frontend/src/api/countdownConfig.js`

---

## 11. 支付 `/payment`

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/payment/create` | 创建支付订单 | JWT |
| POST | `/payment/notify` | 支付宝异步通知 | 否 |
| GET | `/payment/return` | 同步回调 | 否 |
| * | 支付订单查询等 | 见 PaymentController | JWT |
| * | 支付配置 | PaymentConfigController | 管理 |

前端：`frontend/src/api/payment.js`

---

## 12. 充值卡密 `/cards`

卡密生成、查询、使用等（`RechargeCardController`）。
前端：`frontend/src/api/card.js`

---

## 13. 公告 `/announcement`

| 能力 | 路径示例 |
|------|----------|
| 管理 | `/announcement/create` `update` `publish` `offline` |
| 查询 | `/announcement/page` `/published` `/top` |

前端：`frontend/src/api/announcement.js`

---

## 14. 系统配置与变量

| 模块 | 前缀 | 说明 |
|------|------|------|
| 系统配置 | `/system/config` | GET 列表 / PUT 更新 / reset |
| 系统变量 | `/admin/variables` | CRUD、按类型查询、启停 |

前端：`frontend/src/api/setting.js`、`variable.js`

---

## 15. 日志与统计

| 模块 | 前缀 | 说明 |
|------|------|------|
| 操作日志 | `/logs` | 分页查询（可走 ES） |
| 统计 | `/statistics` | 首页/报表统计 |

前端：`frontend/src/api/log.js`、`statistics.js`

---

## 16. 对外开放 API `/external`

面向第三方 / 用户 API Key 调用（路径以实现与兼容旧 PHP 接口为准）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/external/getmoney` | 查询余额 |
| POST | `/external/chadan` | 查单 |
| POST | `/external/budan` | 补单 |
| POST | `/external/add` | 下单 |
| POST | `/external/query-courses` | 查课 |
| POST | `/external/query-progress` | 查进度 |
| POST | `/external/get-platforms` | 平台列表 |

前端参考：`frontend/src/api/external.js`
页面：`/api-guide`（`ApiDocs.vue`）

---

## 17. 客服

`CustomerServiceController` 提供会话与消息相关接口。
前端：`frontend/src/api/customerService.js`
管理页：`/admin/customer-service`

---

## 18. 运动跑量 `/sport-run`

`SportRunController` 是 TODO 占位，并被 `@PreAuthorize("denyAll()")` 禁用，不能作为已实现接口调用。原生插件业务使用下述独立 `/services`、`/service-orders` 接口，不解除该占位接口的保护。

---

## 19. 在线文档

开发环境启动后端后访问：

```
http://localhost:8080/api/doc.html
```

Docker 生产 profile 下默认：

```
API_DOC_ENABLED=false
```

如需开启，在环境变量或 `application-prod.yml` 中设置 `knife4j.enable=true` / `API_DOC_ENABLED=true`。

---

## 20. 错误排查提示

1. 404：是否漏了 `/api` 前缀，或开发/生产端口混用（8080 vs 8082）
2. 401：Token 过期，调用 `/auth/refresh` 或重新登录
3. 403：非管理员访问 `/admin/**`
4. CORS：检查 `course.security.allowed-origins`
5. 405：多为 Nginx 反代方法限制，见 [DOCKER_FIX_405_CORS.md](./DOCKER_FIX_405_CORS.md)


---

## 21. 插件集成 `/admin/plugin-integrations`

权限：`api-provider:update`。本节是管理员接入检查，查询本身**不安装 PHP、不创建订单、不扣款/退款**。原生业务入口见第 22 节，不能以检查页代替用户下单功能。

| 方法 | 路径（相对本节前缀） | 说明 |
|------|----------------------|------|
| GET | 空路径 | 全部 12 包研究证据、接入状态与已实现能力 |
| GET | `/{pluginId}/providers` | 精确匹配插件类型的分页安全配置摘要；无凭据/URL |
| GET | `/{pluginId}/providers/{providerId}/catalog` | P01 闪电报价（project=sdxy/ydsjxy/xbd）、P03 黑鲨商品、P04 极光商品、P10 无心报价、P05总公里两方案报价（project=xbd，业务仍默认关闭） |
| GET | `/{pluginId}/providers/{providerId}/schools` | 仅 P04 极光学校检索 |

目录/学校请求必须使用**已验证且已启用**的保存配置。学校和配置列表支持 `page=1…10000`、`pageSize=1…100`、`keyword` 至多 80 字符。Flash 一次请求只读取一个白名单项目；P05可传xbd；其余目录类型拒绝非空 project。商品金额 `unitPrice` 以十进制字符串返回；学校只返回 `items/page/pageSize/hasMore`，不虚构 total。

页面：`/admin/plugin-integrations`。运行步骤、协议形状与边界见 [PLUGIN_INTEGRATIONS.md](./PLUGIN_INTEGRATIONS.md)。


## 22. 原生服务商城与订单（默认关闭）

`APP_NATIVE_SERVICES_ENABLED` 默认为 `false`；须经授权核查并执行所需迁移018–029后启用，029是精确计价的前置条件。运行镜像包含六类服务及计价实现，但最新只读核查中业务开关仍关闭；本次未读取生产数据库，不能由代码发布推断迁移完成或业务可调用。均需登录，返回 `Cache-Control: no-store`；用户只能访问所属订单，金额均为十进制字符串。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/services` | page / pageSize，支持 providerType 分类，先过滤后分页 |
| POST | `/services/{id}/lookup` | 授权账号查询，表单字段白名单；不返回密码/授权码 |
| GET | `/services/{id}/schools` | 极光及两类实习服务学校检索；支持page、keyword，AppUI项目3/6/8为名称标识、最多150页，分页不透传路由元数据 |
| POST | `/services/{id}/quotes` | quantity、distance、fields、taskTimes、authorizedAccount、schedule（实习）；生成五分钟报价，不扣款 |
| GET | `/service-orders`、`/service-orders/{id}` | 本人订单与可用操作；含版本号，不含供应商凭据。Flash 未知 completed、实习与总公里 completed 返回 null，不等于 0；实习含 schedule，总公里含 distancePlan，数量单位由 quantityUnit 区分 |
| POST | `/service-orders/{id}/sync` | 精确核对已绑定编号；总公里服务仅更新提交状态，其他服务读取进度；不自动凭状态退款 |
| GET | `/service-orders/{id}/options` | 无心及两类实习当前计划与可选值；sxdk_tw额外返回paidDates，不返回密码/账号；未返回项目不填默认值 |
| POST | `/service-orders/{id}/quotes` | action、quantity、fields、schedule；预览按能力开放的退款/增次/暂停/恢复/整单或单任务延期/改时/编辑/重分配/实习续期/立即执行/补交 |
| GET | `/service-orders/{id}/events`、`/service-orders/{id}/logs` | 本平台操作记录 / 有界上游执行记录 |
| POST | `/service-order-operations/{id}/confirm` | 使用服务端确认编号，至多派发一次，不接受客户端金额 |
| GET | `/service-order-operations/{id}` | 读取提交结果；UNKNOWN 不能自动重放 |
| GET/POST | `/admin/service-products` | 商品列表 / 新建；`api-provider:update` |
| PUT | `/admin/service-products/{id}` | 带版本号更新；绑定不可修改，下架不依赖上游在线 |
| GET | `/admin/service-orders` | 管理列表；`api-provider:update` |
| GET | `/admin/service-orders/{id}/audit` | 上游单号、所属用户和核对证据；同时要求接口管理与财务核对权限 |
| GET | `/admin/service-order-operations/{id}` | 管理员读取结果，同上双权限 |
| POST | `/admin/service-order-operations/{id}/resolve` | UNKNOWN 核对：outcome、externalOrderNo、refundedUnits、evidence、upstreamChecked |
| POST | `/admin/service-orders/{id}/refund-quotes` | 上游主动退款核对预览：orderVersion、refundedUnits、evidence、upstreamChecked |
| POST | `/admin/service-order-operations/{id}/settle-refund` | 确认上一步本地入账；没有上游写请求，同上双权限 |

财务核对的两个权限为 `api-provider:update` 和 `payment:reconcile`。证据至少十字，不得填入密码或密钥。上游超时、负码、缺失必要回执进入 UNKNOWN；人工确认“未受理”才退回预留扣款。退款按冻结零售价、真实次数与已付净额封顶，账本与状态更新同一事务。

各类业务表单和已知缺口、资金状态机及上线前置条件见 [PLUGIN_INTEGRATIONS.md](./PLUGIN_INTEGRATIONS.md)。

### 22.1 实习服务专用字段

实习商品的 `providerType=sxdk_tw`，`project=remoteProductId`，值为 `zxjy/qzt/gxy/xyb/xxy/xxt/hzj/gxzy/jxzhjy`。发布还需 `contractPrice: { unitCost, validUntil, evidence, upstreamChecked: true }`，有效期至多 90 天；合同成本及核对资料只返回给商品管理员。

下单为 `quantity: 0`、`distance: null`、`taskTimes: []`、`authorizedAccount: true`。`fields` 接收账号/密码、姓名、岗位、地址、经纬度及已选学校等白名单字段，不接收 `act/key/url`。账号读取时校友帮可指定 `runMode`（1/2/3），实际下单以 `schedule.runMode` 为准。

`schedule` 包含：

- `endDate`（含当天）、`weekdays`（1=周一 … 7=周日）、`checkInTime`、可选 `checkOutTime`；工学云/黔职通必须设置下班时间。
- `runMode`、`dailyReport/weeklyReport/monthlyReport`、`skipHolidays/randomLocation`。
- `weeklyReportDay`（1…7）、`monthlyReportDay`（0=月底，1…31）、可选 `reportLengths`，其中 day/week/month/summary 均为 `{minSize,maxSize}`，0…10000。

`EDIT_SCHEDULE` 使用 `quantity: 0`、完整 schedule 与变更 fields；账号不可修改，空密码保留原凭据。`RUN_NOW` 无参数，独立按一次计价；`REPORT` 使用 `{startDate,endDate,reportType}`，范围内每一天都须已购买且不晚于今天。校友帮运行方式不能通过编辑变更。退款按本平台已公布的未用服务日规则结算，不把上游取消确认误当成其退款天数证明。

账号预检 `/services/{id}/lookup` 还可返回 `advice: {checkInTime, checkOutTime, endDate, weekdays, dailyReport, weeklyReport, monthlyReport}`，字段可缺失。其星期统一为 1=周一…7=周日。`advice` 与已购 `paidDates`、当前 `schedule` 不同；客户端显式采用建议，不自动购买或改价。

### 22.2 闪电单任务延期

`action=DELAY_TASK`、`quantity=0`、`fields={taskId,page}`。后台重新查询本订单对应日志页，只有唯一且未开始/失败的任务可预览。任务编号和聚合订单号从本地归属及上游记录核实，客户端不可传入其他订单号。生成零金额确认编号后显式确认；只影响该任务，不恢复已暂停的整单，未知结果不能重放。

### 22.3 闪电短期账号授权

所有接口要求登录，响应不缓存。授权、验证码与刷新请求不会自动重试。

| 方法 | 路径 | 参数 / 行为 |
|------|------|------|
| POST | `/services/{id}/account-sessions` | `{mode: "PASSWORD" 或 "SMS", account, schoolName, authorizedAccount: true}`；生成最多十分钟的会话，无上游请求 |
| POST | `/service-account-sessions/{id}/send-code` | 无业务参数；仅 sdxy SMS 会话，受手机号及用户额度保护 |
| POST | `/service-account-sessions/{id}/verify` | `{secret}` 为此会话的密码或验证码；最多派发一次 |
| GET | `/service-account-sessions/{id}` | 查询状态，READY 才返回安全 lookup；不返回明文账号/学生 ID/密码/验证码 |
| POST | `/service-account-sessions/{id}/refresh-rules` | 仅已授权 sdxy/xbd，会话内只派发一次；无法重新获取规则时返回 REAUTHORIZE |
| DELETE | `/service-account-sessions/{id}` | 撤销本地授权、清除加密快照，无上游请求 |

会话状态为 CREATED、SMS_SENDING、SMS_SENT、VERIFYING、READY、REFRESHING、REAUTHORIZE、UNKNOWN、EXPIRED、REVOKED、USED。UNKNOWN 不代表确定失败，不能重放验证码或刷新动作。

闪电下单 `/services/{id}/quotes` 可指定顶层 `accountSessionId`，`fields` 只传服务类型/计划/区域等选项，不传 account/password/studentId。报价绑定会话版本且不晚于会话到期；刷新或撤销后必须重新预览。确认下单与消费会话共用余额事务，防止同一授权的多个报价重复创建订单。


### 22.4 黑鲨官方托管人脸授权

仅用于 `heisha/default` 商品 3/4。使用 22.3 的创建会话、验证、状态查询和撤销端点；mode 必须为 PASSWORD，account 为本人手机号，不提供短信或规则刷新。预检后返回 FACE_REQUIRED 与安全 lookup，尚不具备下单权限。

| 方法 | 路径 | 参数 / 行为 |
|------|------|------|
| POST | `/service-account-sessions/{id}/face-collection` | `{authorizedFace: true}`；绑定手机号获取一次官方采集入口；未配置批准域名则不派发 |
| POST | `/service-account-sessions/{id}/face-check` | 无业务参数；从服务端读取令牌检查采集，不接受客户端提供的手机号/令牌 |
| POST | `/service-account-sessions/{id}/face-launch` | 无业务参数；返回 `{sessionId,ticket,expiresAt}`，ticket 为 60 秒内单次使用的本地跳转凭据 |
| POST | `/service-face-collection/launch` | `application/x-www-form-urlencoded` 的 `{sessionId,ticket}`，禁止查询字符串；原子消费后 303 到批准的官方地址 |

除最后的单次凭据消费端点外均要求登录并核实所有者。该消费端点不是匿名查询接口：只接受此前由会话所有者获得的随机凭据，验证有效期、状态、配置与一次性摘要；GET、跨站导航及错误凭据均不能获得采集地址。所有成功跳转和错误响应不缓存，跳转使用 `Referrer-Policy: no-referrer`。

人脸状态为 FACE_REQUIRED → COLLECTING → FACE_PENDING → FACE_CHECKING → READY。未完成时回到 FACE_PENDING；只读检查失联为 FACE_RETRY，可手动再查。入口创建失联为 UNKNOWN，不能重放。`face` 仅含 `collectionOrigin`、`status`（completed/fileCount/minFileCount/maxFileCount）和可用操作标记，绝不包含照片或令牌字段。普通 GET 只读取本地状态，不自动调用上游。

只有 READY 可用顶层 `accountSessionId` 预览商品 3/4；fields 仅传 `planOptionId`、`fenceOptionId`、`runTime`，quantity/distance 按次与公里计费。服务器从会话读取预检及人脸凭据，确认时消费会话与扣款在同一事务中。客户端不能用旧密码入口、伪造令牌、跨商品/用户会话绕过采集检查。


### 22.5 实习订单本人通知

依赖迁移 023，并且 `NATIVE_SERVICES_ENABLED` 与 `NATIVE_SERVICE_NOTIFICATIONS_ENABLED` 都须经授权开启；默认均为 false。本功能是本平台的 ShowDoc 通知，不是校友帮微信授权/绑定。仅限已确认的本人 `sxdk_tw` 订单，管理员角色不绕过归属校验；响应禁止缓存。保存和只读查询均不发送通知。

| 方法 | 路径 | 请求 / 行为 |
|------|------|------|
| GET | `/service-orders/{id}/notifications` | 查询设置版本、是否配置/验证/启用、deliveryAvailable、canVerify、原验证记录 ID 和有效期；不返回密钥或验证码 |
| PUT | `/service-orders/{id}/notifications` | `{token, version, consent:true}`；只保存并加密，清除旧证明和待发消息。token 必须为 16–128 位字母/数字/下划线/连字符，不能传 URL |
| POST | `/service-orders/{id}/notifications/challenge` | `{version, consent:true}`；持久化后派发一次验证通知，十分钟有效。同配置重复操作复用原记录；响应丢失只 GET 恢复 |
| POST | `/service-orders/{id}/notifications/verify` | `{code, version, consent:true}`；六位验证码，最多五次失败，失败计数提交。实际收到的码可验证 UNKNOWN 发送记录 |
| DELETE | `/service-orders/{id}/notifications` | `{version, consent:true}`；停止并清除密钥/证明、取消未派发消息；通知发送开关关闭后仍可停止 |
| GET | `/service-orders/{id}/notifications/deliveries` | 本人发送记录，page=1…10000、pageSize=1…100；不返回消息正文 |
| GET | `/service-notification-deliveries/{id}` | 查询本人单次发送记录；不再次发送 |

记录 `kind=VERIFY_RECEIVER/ORDER_UPDATE`，`state=READY/DISPATCHING/ACCEPTED/REJECTED/UNKNOWN/CANCELLED/EXPIRED`。ACCEPTED 仅表示 ShowDoc 受理，不是本人已阅读；UNKNOWN 不自动重发。密钥更换须重新验证；订单转属后不继承旧渠道或证明。

专用有界工作线程观察最新本地订单记录，不查询上游、不执行考勤、不改余额。更新可能合并；仅发送本平台订单编号、状态、计划服务日，不推断完成次数。原订单“周期结束”不等于考勤成功。请求固定发往官方 ShowDoc HTTPS 地址，由 `SafeHttpClient` 校验出站目标；客户端不能决定目标 URL。限流不可用时不发送，过期队列内容清除；停用不能撤回已派发内容。

### 22.6 P05总公里服务（已实现，默认关闭）

独立服务配置 `providerType=ssbenz_xbd`，商品 `project=xbd`、`remoteProductId=0/1`，销售单价单位为元/公里。方案编号不能直接解释为某种跑步类型。管理员配置HTTPS API目录与API Key即可，不填UID；保存、验证、启用、商品上架分别操作。令牌是唯一可验证的账户身份，规范化目录与令牌由服务端密钥HMAC绑定；更换令牌后旧订单停止自动读取，不能自动迁移。

`POST /services/{productId}/quotes` 的示例（只预览，不扣款）：

```json
{
  "quantity": 1,
  "distance": "120.50",
  "fields": {
    "account": "本人账号",
    "password": "本人密码",
    "schoolName": "",
    "startTime": "09:05",
    "endTime": "21:10",
    "weekdays": "1,3,5"
  },
  "taskTimes": [],
  "schedule": null,
  "accountSessionId": null,
  "authorizedAccount": true
}
```

- 数量只能为1单；`distance`是总公里数0.01–999999.99、最多两位小数，不是每次距离。时段06:00–22:59且结束晚于开始；星期非空、不重复，学校选填。其他字段、按次追加、账号会话及任务时间列表不接受；其他次数服务的距离上限仍为50。
- 报价及订单都返回无凭据 `distancePlan={typeCode,totalDistance,schoolName,startTime,endTime,weekdays}`，其中距离是字符串、星期是整数数组。报价绑定快照、商品版本与原确认编号，复用现有确认/结果查询API。售价0.25元/公里、120.50公里的最终扣款为30.13元，只作一次最终分值舍入。
- 创建回执只表示已记录提交：订单 `SUBMITTED` 或后续 `SUBMISSION_REVIEW`；`completed=null`、`quantity=1`、`quantityUnit=单`，不显示完成率。状态读取缺失/矛盾/超限时保持原记录，不据此退钱；没有账号预检、学校查询、单笔日志、在线编辑或自动退款。
- UNKNOWN只检查原操作，不重新发送。管理员仍须接口管理与财务核对双权限及至少十字依据；`refundedUnits`必须省略或为null。ACCEPTED只绑定已核实的正整数提交编号，NOT_ACCEPTED返还原扣款一次；不能将公里数或1单换算成退款次数。
- 计划复用现有018/019/020数据结构，精确计价另依赖029；本次没有生产迁移或启用动作；执行时段、价格和真实接口响应需授权验收。

较早P05本地检查点通过1303项后端、99项前端单元、20组实际Vue浏览器流程及构建，另有28项隔离MySQL8.0.44存储断言。最新完整检查点见22.7；上述历史数据不能代替真实接口验收或业务启用。

### 22.7 计费单价精度与报价响应

迁移 `029_native_service_price_precision.sql` 将订单及操作的 `unit_charge` 扩为 `DECIMAL(18,8)`，保留原整数范围。除22.12鲸鱼逐次分值计费协议外，新订单的售价×计费因子不进行中间舍入；只将最终金额保留两位小数。历史订单与READY报价不重算，后续增次/退款仍遵守原冻结单价。

`POST /services/{id}/quotes`、操作预览及原操作GET/确认响应中的 `QuoteView` 新增可空字符串 `unitCharge`，例如：

```json
{
  "id": "91000000-0000-4000-8000-000000000001",
  "action": "CREATE",
  "state": "READY",
  "quantity": 3,
  "quantityUnit": "次",
  "unitCharge": "0.05499989",
  "amount": "0.16"
}
```

- `unitCharge` 是已冻结的每个计费数量单价，不是当前商品展示价格；最多八位小数。金额和单价均为十进制字符串，禁止客户端改写或推算确认金额。
- 计费/退款动作有有效数量时可返回该字段；没有数量的免费动作返回null。旧响应可缺少它；客户端隐藏明细而不是反算单价。总公里服务继续使用 `distancePlan` 摘要，不能把1单解释为可退次数。
- 原报价若记录 `unitCharge="0.05500000"`、`amount="0.17"`，仍按该合同确认，不能显示或收取新价格0.16元。确认请求只使用原操作编号；GET恢复不再次扣款或派发。
- 退款依真实次数、原报价上限与实付可退净额封顶。例如两次各付0.16元后，可退净额为0.32元，即使单价×六次舍入到0.33元也不能多退。
- 缺失/部分精度迁移导致保存值与快照不等时，事务回滚并返回业务错误；订单创建核验在扣款之前完成，不派发。退款双权限、所有权、UNKNOWN核对和幂等规则不变。

2026-09-10精确计价历史完整本地验收（不是本轮自动核对验收）：1327项后端、109项前端单元、22组浏览器工作流、10项研究工具回归与前后端构建通过；763份参与验证的源码/配置/数据结构摘要未变。029另通过48项隔离MySQL8.0.44存储与迁移断言；不等于真实资金或供应商验收，生产迁移状态仍须授权核查。详细范围见 [集成说明第6节](./PLUGIN_INTEGRATIONS.md#6-开发验证)。

### 22.8 订单进度与实习计划核对信息

订单列表、详情及手动更新响应中的 `OrderView` 新增可空对象 `statusCheck`，闪电、黑鲨、极光、无心、雷电、鲸鱼及两类实习（`sxdk_tw/appui`）订单返回，总公里订单不返回；雷电取消后的记录不再提供核对对象。例如：

```json
{
  "statusCheck": {
    "checkedAt": "2026-09-11T10:20:30",
    "delayed": true
  }
}
```

- `checkedAt` 是最近**成功核对**的时间字符串或null；无偏移时按北京时间解释，不是执行时间、业务更新时间或最近尝试时间。`delayed=true` 表示最近查询失败，仍保留此前核对时间与业务结果；尚无成功记录可同时返回null/true。
- 旧响应可缺少整个对象；客户端隐藏新区域，不拿 `createTime/updateTime` 代替。响应不暴露租约、令牌、失败正文或内部计数。
- `POST /service-orders/{id}/sync` 仍是本人手动读取；与自动任务共享逐单5分钟租约，忙碌时返回业务错误，不再次发起读取。成功或失败后，客户端最多GET一次本地列表来刷新已保存信息；该GET不触发远程读取。管理员列表仅展示核对信息，无新增管理员sync入口。
- 自动任务没有HTTP触发入口。`NATIVE_SERVICE_STATUS_REFRESH_ENABLED=false` 默认关闭且依赖总开关；只处理上述八类的 `ACTIVE/PAUSED/ATTENTION/REFUND_REVIEW`（雷电的`REFUND_REVIEW`除外）、有效用户、有编号且无待核对写操作的订单，`COMPLETED`不自动重复查询。成功至少5分钟后再查、失败退避至60分钟。不重下单、不退款、不通知，不保证完成时效。
- 实习 `status` 表示计划状态：运行/暂停码1/2分别映射 `ACTIVE/PAUSED`，其他合法未知码为 `ATTENTION`，非法类型或格式是查询失败。仅在日历一致且状态为1/2时，截止日早于北京时间今天才映射 `COMPLETED`；截止日当天不提前结束。实习 `COMPLETED`应显示“服务期结束”，不是考勤完成；`completed`始终null，不展示完成进度条，不按日期估算出勤。查询保留已购日历、内部完成数、金额及权益。
- 实习读取按已有编号和项目精确匹配，每页100条、最多10页，5秒后不开始下一页；校验每个读取页的全部行并拒绝已读页面中的重复编号，匹配页校验完成即返回，不保证未读取页的全局唯一性。这是有界状态查询，不是账户全量扫描或缺号自动发现，不按凭据猜订单归属。
- 应用结果前重新校验租约、业务版本、编号、身份和配置；迟到或冲突响应不覆盖订单。仅核对元数据变化不增加业务版本，不使READY报价无故失效；`REFUND_REVIEW`不被自动清除。
- **部署包含此DTO/实体的新后端前必须完整应用 `030_native_service_status_refresh.sql`，即使任务开关关闭。** 迁移只增加维护字段、约束与索引。本轮未部署；本地验证不代表生产迁移、真实协议或资金流程验收。

2026-09-11实习扩展本轮本地验收：1496项后端、117项前端单元、23组实际Vue浏览器流程及前后端构建通过，前端构建于15:04:46（UTC+8）完成。浏览器23组连续串行通过，实际Vue桌面/手机深色已复查；使用模拟业务响应，未部署或调用真实接口。本批无新增迁移，未重跑MySQL或归档扫描工具。较早自动进度核对检查点的1387项后端、113项前端及030的93项隔离MySQL断言仅为历史结果，不计入本轮；完整范围与限制见[开发验证](./PLUGIN_INTEGRATIONS.md#6-开发验证)。

### 22.9 已保存服务订单检索（新实现，未部署）

`GET /service-orders` 与 `GET /admin/service-orders` 支持下列可选条件，先组合过滤再分页；详情接口不变。管理员列表仍要求 `api-provider:update`，使用本人列表时即使有管理权限也只返回本人的订单。

| 参数 | 范围及语义 |
|------|------------|
| `page` / `pageSize` | 1–10000 / 1–100，默认1 / 20；前端每页20条 |
| `keyword` | 最多100字符，订单UUID、服务名称或已显示的脱敏账号标识的字面子串；`%`、`_`、`!`不是通配符，不支持明文账号/密码检索 |
| `providerType` | `flash/heisha/jiguang/wuxin/sxdk_tw/appui/leidian/jingyu/ssbenz_xbd` |
| `status` | `ACTIVE/PAUSED/COMPLETED/CONFIRMING/ATTENTION/REFUND_REVIEW/SUBMITTING/SUBMITTED/SUBMISSION_REVIEW/REFUNDED/CANCELLED` |
| `createdFrom` / `createdTo` | `YYYY-MM-DD`，年份1000–9998；按北京时间含首尾日期，截止条件实际为次日零点之前；拒绝无效日期和倒置范围 |
| `orderId` | 精确匹配小写UUID，可定位不在首页的订单；不能访问或不存在的编号均返回空列表 |
| `ownerId` | 仅管理员列表可用，正数64位用户编号；本人列表拒绝该条件，不能通过传入本人或其他编号扩大范围 |

`status` 对应列表展示状态：存在待确认操作的订单属于 `CONFIRMING`，不会混入 `ACTIVE` 等其他状态结果。其余条件均为AND组合；关键词的三个OR字段始终限制在授权范围内。结果按 `createTime DESC, id DESC` 稳定排序，返回 `records/total/current/size`，保留 `Cache-Control: no-store`。

检索只读取已保存数据，不解密凭据、不调用供应商、不更新进度、不生成报价，也不改变账本或订单。无新增迁移，不改变启用开关。页面将草稿和已应用条件分开；刷新、换页及失败重试使用已应用条件，重试不会回到第一页。读取失败与无匹配结果分别显示，旧列表请求会在新查询、账号/权限范围变化或卸载时取消并隔离。搜索内容不写入URL或浏览器持久存储；购买后的 `focus` 仅用于精确订单定位，非法定位不会退回全列表。

### 22.10 AppUI按天实习（P09，原生协议实现）

`providerType=appui`，`project=remoteProductId`严格为字符串`1`至`9`，九类实习项目走同一原生商城与订单API。管理员目录读取使用`/admin/plugin-integrations/P09/providers/{providerId}/catalog?project=3`；学校是商品级`/services/{id}/schools`，不是通用接入页学校能力。项目3/6/8要求选校，每页20条、page至多150、keyword至多80字符；学校响应的`id`与`name`均为同一校名（最多100字符）。其他项目不接受学校。

下单字段示例（仅虚构数据）：

```json
{
  "quantity": 10, "distance": null, "schedule": null, "taskTimes": [],
  "authorizedAccount": true, "accountSessionId": null,
  "fields": {
    "account": "example-student", "password": "example-only-password", "schoolName": "示例职业学院",
    "address": "示例市实训路18号", "startTime": "08:17", "endTime": "17:43",
    "weekdays": "1,2,3,4,5", "reports": "1,2"
  }
}
```

- `quantity`为1–365天，不接受距离、日历、账号会话或任务数组。`fields`只接收示例中的8个键；查询返回只读姓名/地址，预览重新查询姓名，不接收客户端`studentName`。密码最多128字符，保留首尾空格；地址最多500字符，时间为北京时间HH:mm且下班晚于上班。星期1–7、报告1=日报/2=周报/3=月报，以不重复逗号分隔的字符串传入，两组均至少选择一项。
- `EDIT_PLAN`为`{action:"EDIT_PLAN",quantity:0,fields:{address,startTime,endTime,weekdays,reports,password}}`，无数量/距离变更；空密码保留原密码。`options`只返回可编辑安排，不返回账号或凭据，不使用新单默认值补齐缺失字段。
- `ADD_TIMES`为增加1–365天，累计最多9999天，沿用冻结订单单价并核对实时成本。报价返回`quantityUnit:"天"`和最多八位小数的`unitCharge`；商品`priceUnit`为`元/天`。金额由服务器精确计算，客户端不能传金额。
- `completed`表示总天数减剩余天数的已用额度，不是签到成功次数；`COMPLETED`不能推断剩余为0。`SYNC`按绑定编号、项目、账号指纹和总量核对，保留金额，不能凭退款状态入账。普通订单不返回内部账号指纹或快照。
- `REFUND`预览与确认复用既有协议；明确退款天数受预览、本地未用量、派发前实时剩余和实付净额多重上限约束。超时、缺字段、非法或超界回执进入UNKNOWN，保留原操作编号，只查询、不重放、不假定退款成功。日志仅展示id/时间/固定签到与签退状态，不暴露原始消息。

本功能复用既有业务和状态核对开关，无新增DDL；整版部署仍须核查030等迁移前置条件。本地模拟和回环HTTP测试不等于真实供应商或生产资金验收；业务启用以已审核的部署配置为准。

### 22.11 雷电运动（P12，原生协议实现）

`providerType=leidian`，`project=remoteProductId`为`1/2/3/4`。下单使用1–100次、每次1–10公里（最多一位小数），`fields={account,zoneId,startDate,startTime,endTime,weekdays}`，须确认账号授权；不接受密码或任务数组。项目1–3重新核对跑区，项目4使用数字手机号且不接受跑区输入。执行日期在北京时间今天至未来一年内，同日时间窗不跨天。

目录价格包含已舍入成本的保守上限；服务端在预览与派发前重新校验，不允许客户端传入金额。业务订单编号与内部记录编号分开绑定；新单UNKNOWN的ACCEPTED核对必须提供 `externalOrderNo` 和 `externalSubOrderNo`，并重新读取、核对账号指纹与计划身份。

动作包括 `EDIT_PLAN/CHANGE_TIME/CANCEL/SCORE_INFO`，不支持增次或直接次数退款。`GET /service-orders/{id}/score-info`仅返回`{text}`纯文本；记录中的`editable/endTime`用于显示可改任务和结束时间。`CANCEL`仅进入`REFUND_REVIEW`，不会自动退钱；取消后不再读取已删除的远程记录，退款须走独立双权限核对入账。已用次数不是成绩成功次数。

### 22.12 鲸鱼运动（P08/P11共用协议的部分实现）

`providerType=jingyu`，仅开放`project=remoteProductId=keep/bdlp`。每单1–365次、每次1–100公里（最多一位小数），`taskTimes`必须为同等数量、互不重复的未来一年内北京时间，格式`YYYY-MM-DD HH:mm:ss`。Keep字段为`account/password/zoneId/minMinute/maxMinute`（配速范围3–6及8–15），步道字段为`account/zoneId/runType`（1有效跑、2自由跑）；预览与派发均重新校验账号、跑区及授权，不使用客户端宣称的授权状态。

Keep按每次距离计价，步道按次计价；此协议先将每次费用按HALF_UP保留两位小数，再乘购买次数，不套用其他协议的最终总额单次舍入规则。冻结单价、原确认编号、实付可退净额上限仍有效。

支持暂停/恢复、部分失败批量延期、未开始任务改时/延期、退款申请及记录查询。成功任务计入完成数，未知或矛盾状态进入关注/核对，不以消费次数推断成功。退款动作没有原子退款数量回执，只进入UNKNOWN/人工核对，不按预估上限入账；管理员提供核实数量后还要重新读取原两个编号并验证退款状态。P08/P11不重复注册；YYD/YMTY及其他分支未开放，模拟协议测试不代表真实服务验收。

## 23. 原生项目中心与子钱包（已实现，默认关闭）

依赖迁移 021 及 `NATIVE_SERVICES_ENABLED`。以下端点全部要求登录，成功响应 `Cache-Control: no-store`，金额/额度返回十进制字符串。普通用户只能操作本人账户；上游客户密钥不出现在任何公共 DTO 中。

| 方法 | 路径 | 参数 / 行为 |
|------|------|------|
| GET | `/service-projects` | page/pageSize；开放项目及本人已有账户，下架不隐藏历史账户 |
| POST | `/service-projects/{id}/quotes` | `{action: PROVISION/TOP_UP/WITHDRAW, units, confirmedPolicy:true}`；开户 units 可留空/零，也可指定初始额度（已实现，受原生业务开关控制） |
| POST | `/project-accounts/{id}/refresh` | 显式查询并校验本人上游余额，不自动执行兑换 |
| GET | `/project-operations`、`/project-operations/{id}` | 本人记录/原操作状态；不会重发请求 |
| POST | `/project-operations/{id}/confirm` | 固定报价编号，至多派发一次；不接收客户端金额或上游客户号 |
| GET | `/admin/service-project-catalog` | providerId；接口管理权限，仅只读目录 |
| GET/POST | `/admin/service-projects` | 管理列表/发布项目；`api-provider:update` |
| PUT | `/admin/service-projects/{id}` | 带 version 更新；供应商/上游项目绑定不可替换 |
| GET | `/admin/project-operations`、`/admin/project-operations/{id}` | 双权限资金核对列表/详情 |
| POST | `/admin/project-operations/{id}/resolve` | `{outcome:ACCEPTED/NOT_ACCEPTED, customerId, evidence, upstreamChecked:true}`；已受理开户才允许填写经核实的 customerId |

发布字段为 providerId、remoteProjectId、title、description、unitPrice、unitCost、validUntil、evidence、upstreamChecked、enabled、version。管理员须核实实际单位成本，售价不低于成本，有效期不超过九十天。目录价不能证明最终成本。每个账户冻结开户费率；充值向上取整到分，转回向下取整到分，并受本平台未退充值额度与付款预算约束。转回不计入新充值总额。

**带初始额度开户（已实现，默认关闭）：** `PROVISION` 的 `units` 为 null/0 时沿用免费零余额开户；正值范围0.000001–100000、最多六位小数。开户费率取发布时的有效售价快照，费用为额度×费率向上取整到分。预览只保存READY，不创建客户、不预扣；用户独立确认后，在创建待处理账户和DISPATCHING操作的同一事务内，使用 `PROJECT_TRANSFER/SYY:{operationId}` 预扣一次，再于事务外只发送一次 `generateCustomer(project_id,balance)`，不是自动执行两次“开户+充值”。

创建回执须包含明确客户编号、项目、凭据及与确认初始额度完全相同的余额；停用、缺字段、余额不一致、响应丢失或本地结算失败均保持UNKNOWN与预扣，不自动重开、追加充值或退钱。成功后加密绑定账户，初始额度和实付费用分别记入可转回数量/预算；转回仍受实时可用余额和冻结费率限制，不兑付赠额。该创建响应不含可靠的实际单位成本，必须依赖有效期内独立核实的合同成本，目录价不能代替成本验收。

双权限核对必须同时核实客户归属、开户结果和初始充值/扣款流水，不能按当前余额推测是否完成充值；ACCEPTED仍只查询已核实客户并按原快照结算，NOT_ACCEPTED只返还原初始预扣一次。经营者列表/账户明细/全局汇总与 `PROJECT_ACCOUNT` 流水均计入成功PROVISION的实际费用，零额开户仍是零资金动作，UNKNOWN/未受理不计已结算金额。界面默认零余额，主动选择充值后才显示初始额度；修改选择/金额撤销规则勾选，预览展示额度、费率、费用，原结果查询不重发POST。复用021表结构，无新增迁移；该功能不是客户额度购买项目服务，也不开放安全业务登录。

开户/资金操作状态：READY、DISPATCHING、UNKNOWN、SUCCEEDED、NOT_ACCEPTED、EXPIRED。用户账户状态：NEW、ACTIVE、DISABLED、BUSY、UNKNOWN。重复确认返回原状态；失联不自动退款。核对要求 `api-provider:update` 与 `payment:reconcile` 同时存在，并提供至少十字的依据。接口主密钥或地址变化会阻断旧账户，不能借此替换上游客户。相同规范化地址和主密钥的重复接口配置不能重复绑定同一客户或工单。金额数值不经过 double；异常指数在展开/格式化之前被拒绝。人工核对保留当前上游明确的停用状态。

## 24. 项目图文工单与补偿审核（代码已部署，默认关闭）

依赖迁移 022，图片扩展复用既有加密 MEDIUMTEXT 草稿/快照，无新增上游工单 DDL。只访问本平台已绑定的本人项目工单，不转发上游宽范围列表，不允许用户填写任意上游工单号。所有写请求无自动重试，正文、图片与审核记录服务端加密保存。普通 DTO 仅返回 `hasAttachment` / `attachmentAvailable` 等标记，不返回图片正文、外链或密文。

| 方法 | 路径 | 参数 / 行为 |
|------|------|------|
| GET | `/project-tickets`、`/project-tickets/{id}` | 本地列表/详情；page/pageSize、可选 accountId；不会自动访问上游 |
| POST | `/project-accounts/{id}/tickets` | `{type:suggestion/bug/compensation,title,description,compensationAmount,imageData?,confirmedPolicy:true}`；仅生成本地草稿与操作编号 |
| POST | `/project-tickets/{id}/reply-quotes` | `{content,imageData?,version,confirmedPolicy:true}`；本人回复预览；文字/图片至少一项非空 |
| POST | `/project-tickets/{id}/refresh` | 显式读取原绑定上游工单，不会按回复相似度解决 UNKNOWN |
| GET | `/project-tickets/{id}/image` | 可选 replyId；仅读取本地加密快照中的主图/指定回复图片，不访问上游 |
| GET | `/project-ticket-operations/{id}` | 查询原操作；普通用户不能读取尚未确认的管理员审核说明 |
| GET | `/project-ticket-operations/{id}/image` | 原操作的私有草稿图片，不发送或刷新工单 |
| POST | `/project-ticket-operations/{id}/confirm` | 原草稿编号，确认发送本人图文提交/回复，最多一次 |
| GET | `/admin/project-tickets`、`/admin/project-tickets/{id}` | 接口管理权限；仅本平台已绑定工单 |
| GET | `/admin/project-tickets/{id}/image`、`/admin/project-ticket-operations/{id}/image` | `api-provider:update`；与本人图片接口相同的私有缓存读取 |
| POST | `/admin/project-tickets/{id}/refresh` | 接口管理权限，显式更新上游回执 |
| POST | `/admin/project-tickets/{id}/review-quotes` | 双权限；`{result:approved/rejected,note,version,upstreamChecked:true}` |
| GET | `/admin/project-ticket-operations/{id}` | 管理查询原操作 |
| POST | `/admin/project-ticket-operations/{id}/confirm` | 双权限，只有原审核人可发送自己的 REVIEW 草稿 |
| POST | `/admin/project-ticket-operations/{id}/resolve` | 双权限；`{outcome:ACCEPTED/NOT_ACCEPTED,evidence,upstreamChecked:true}`；只核对原请求，不重发 |

草稿十分钟有效。重复确认不重复发送；过期、供应商配置变化或快照更新阻断旧预览。已关闭/已解决工单不再回复或审核。补偿申请额度大于零且最多六位小数；非补偿类型不得携带非零额度。标题至多 120 字、正文/回复至多 4000 字。

`imageData` 为 PNG/JPEG data URL，原图最多 2MiB、单边 4096、总 400 万像素；服务端只复制像素并重编码 PNG（最多 4MiB），清除元数据。含图片的完整 JSON（包括 chunked）最多 3MiB，超限 HTTP 413。前端选图只作本地预览，用户须明确同意经平台转发至当前绑定上游。私有图片须 JWT 与 owner/真实管理员权限校验，返回 PNG、no-store、nosniff、限制 CSP 与同源资源策略；前端按需读取 Blob，关闭/换图释放临时 URL，不持久化正文。

仅工单回执允许最多 8MiB 响应、100 条回复和总计 6MiB 的规范化内嵌图片字符串；普通文本仍限 8192 字符，资金/目录等非工单响应仍限 256KiB，安全出站策略不变。上游外链、SVG 或无法安全解析的附件只保留标记，绝不下载；超限或解码并发过载不会截断后伪称成功。

确认时转发规范化的 `image_data`，图像回显不匹配/丢失或本地快照结算失败均保留 UNKNOWN，不重发。回复预览冻结既有回复 ID；确认和人工已受理核对必须匹配本次文字/图片及新增回复 ID，不能用先前同内容回复冒充本次成功，刷新也不清除该证据。历史纯文本草稿保持兼容。

**审核通过不等于付款。** REVIEW 不增加平台余额、项目充值预算或账本流水。未知的新工单缺少可独立验证的客户归属，禁止 ACCEPTED 认领；只有已经绑定的回复/审核可在真实证据和只读回执核对后完成本地结算。其他文件/外链附件、安全上游项目登录及真实上游密钥生命周期仍未实现；独立的本平台项目密钥与原生 OpenAPI 见第25节。


## 25. 下游客户、本地子账与项目 OpenAPI（代码已部署，默认关闭）

依赖迁移025与既有项目表021。界面 `/project-clients` 管理本人在本平台的下游客户，一个项目可以创建多个客户；与 `/service-projects` 的上游独立钱包不同。本节所有金额操作只涉及本平台的本地子账，不请求真实供应商、不宣称购买或执行项目服务。

### 25.1 登录用户接口

均要求JWT及有效本人账号，返回 `Cache-Control: no-store`。操作归属依据本人owner，不允许管理员角色绕过。

| 方法 | 路径 | 行为 |
|------|------|------|
| GET | `/project-clients/catalog` | 本地项目目录；page/pageSize（默认1/50，最多100） |
| GET | `/project-clients` | 本人客户；projectId可选，分页默认1/20 |
| GET | `/project-clients/{id}` | 本人客户详情、冻结单价及实付可退预算；无密钥 |
| GET | `/project-clients/stats` | 本人客户/可用客户数、已APPLIED操作数、累计充值与转回 |
| POST | `/project-clients/quotes` | 生成五分钟操作预览，参数见下；不扣款 |
| POST | `/project-clients/{id}/status` | `{version,status:"ACTIVE"|"SUSPENDED"|"CLOSED",consent:true}`；关闭须额度和预算归零，并撤销客户密钥 |
| GET | `/project-client-operations` | 本人操作记录，clientId可选，分页默认1/20 |
| GET | `/project-client-operations/{id}` | 查询原操作；READY/APPLIED/STALE/EXPIRED |
| GET | `/project-client-operations/by-request/{requestId}` | 预览响应丢失后的幂等恢复，只读 |
| POST | `/project-client-operations/{id}/confirm` | `{consent:true}`，同一原操作本地原子结算，不重放已APPLIED |
| GET | `/project-api-keys/{subject}` | 密钥设置/前缀/版本/有效期，不生成、不返回明文；subject为OWNER或本人客户UUID |
| POST | `/project-api-keys/{subject}` | `{version,password,access:"READ_ONLY"|"MANAGE"|"SUPPORT",days:1…365,consent:true}`；签发或轮换，仅此响应返回 `{secret,settings}` |
| DELETE | `/project-api-keys/{subject}` | `{version,password,consent:true}`；立即撤销旧凭据，不删除客户余额 |
| GET | `/project-api-calls` | 本人API调用安全动作/结果记录，不含正文/凭据，分页默认1/20 |

预览请求：`{requestId:"UUID", action:"OPEN"|"TOP_UP"|"WITHDRAW", projectId, clientId, label, units:"10", consent:true}`。OPEN不传clientId、需label（1…100字），可零或带初始额度；其余必须传本人的clientId且不传label。units最多6位小数、单次最多100000；非OPEN必须大于零。同owner/requestId唯一：参数相同返回原操作，不同则拒绝，不猜测此前结果。

金额为字符串：客户使用本平台已发布并冻结的单价，不照搬原PHP浮点费率公式。充值向上取整到分，部分转回向下取整；最终全部转回返还净实付预算中的舍入余款，不得超过客户可退单位/实付预算。客户、平台钱包、不可变账本及操作状态一并提交或一并回滚。关闭原生功能后仍允许本人读取已有结果及撤销密钥，不再开户、充值或转回。

### 25.2 供下游服务端调用的新原生接口

统一前缀 `/external/projects/v1`。必须使用**恰好一个** `X-Project-Key` 请求头，禁止key/api_key/token/customer_api_key查询参数；JWT或既有课程APIKey不能代替。此为新原生REST契约，**不提供原PHP action=generateCustomer等接口的原样兼容层**。密钥应只在可信服务端保存，不能放浏览器存储、URL或日志。

- 主密钥 `npo_…`：READ_ONLY可读本人目录/客户/记录；MANAGE可额外生成并确认本地资金操作和变更客户状态。每次资金事务重新核对owner状态、凭据版本和权限。
- 客户密钥 `npc_…`：默认READ_ONLY可查本人余额和工单；显式SUPPORT可额外提交/回复自己的工单。不能查看owner冻结价格、可退预算、客户标签或其他客户，不具备审核、充值、转回、列目录/客户或管理员权限。MANAGE只允许主密钥，SUPPORT只允许客户密钥，不能交叉使用。
- 所有密钥只保存SHA256，明文只在签发响应出现一次；有效期、暂停、关闭、轮换和撤销都会影响访问。源台密钥不是供应商项目登录密钥。

| 方法 | 后缀 | 权限与行为 |
|------|------|------|
| GET | `/catalog`、`/clients`、`/stats` | 主密钥只读；分页、筛选与登录用户接口一致 |
| GET | `/clients/{id}` | 主密钥限本人；客户密钥限自身且只返回安全余额视图 |
| GET | `/self` | 客户密钥，仅 `{id,projectId,projectTitle,status,balance}` |
| POST | `/quotes` | MANAGE主密钥；参数同上，requestId须由调用方稳定保存 |
| GET | `/operations`、`/operations/{id}`、`/operations/by-request/{requestId}` | 主密钥只读查询原结果，不结算 |
| POST | `/operations/{id}/confirm` | MANAGE主密钥，`{consent:true}`；只确认原编号，不自动新建或重试 |
| POST | `/clients/{id}/status` | MANAGE主密钥，参数同上；不直接改余额 |

响应使用本平台统一 `Result` 结构，金额不转浮点。预览或确认响应丢失后先GET原编号；若需再次提交，必须沿用原请求/操作编号，并显式确认。API调用记录仅用于观察，统计写入失败不会改变原业务结果。本地下游工单见第26节；仍未开放额度消费、真正项目业务入口及旧PHP协议兼容。


## 26. 下游客户本地售后（代码已部署，默认关闭）

文字售后依赖迁移026及025；可选图片另需迁移027，图片扩展代码已部署，迁移027尚未应用。不复用上游 `project-tickets`。JWT网页前缀 `/project-client-tickets`；`X-Project-Key`外部调用前缀 `/external/projects/v1/tickets`，后缀与请求体一致。

| 方法 | 后缀 | 行为 |
|------|------|------|
| GET | 空 | 本人列表，clientId/status/kind可选，page/pageSize默认1/20，最多100条；客户密钥强制限定自身clientId |
| GET | `/{id}` | 工单、申请金额、状态、版本、公开审核结论，不含owner私有费率或密钥 |
| GET | `/{id}/replies` | 沟通记录，按版本升序分页，author为OWNER/CUSTOMER，可有imageId |
| GET | `/images/{imageId}` | 同owner/确切客户鉴权，按需返回PNG二进制而非Result/base64；no-store/nosniff，不公开直链 |
| GET | `/by-request/{requestId}` | 仅查询当前调用身份的不可变请求回执；无记录返回null，不产生工单 |
| POST | 空 | `{requestId,clientId,kind,title,description,requestedAmount,consent:true,imageData?}` 新建 |
| POST | `/{id}/replies` | `{requestId,version,content,consent:true,imageData?}` 回复当前版本；content可为空但必须有图片 |
| POST | `/{id}/decision` | `{requestId,version,action,note,consent:true}` 经营者处理；仅JWT本人/MANAGE主密钥 |

- kind为SUGGESTION/BUG/COMPENSATION；标题1…120字，描述/回复1…5000字，审核说明1…2000字，均为纯文本。requestedAmount为非负十进制金额，最多两位小数、99999999.99，仅补偿申请可非零；其余传`"0"`。金额只是申请参考，不是支付指令。图片走独立imageData字段，账号凭据禁止混入文字或图片。
- 状态OPEN→IN_PROGRESS（回复）；普通工单action为RESOLVE/CLOSE，补偿申请为APPROVE/REJECT，结束后不可继续回复/处理。APPROVE写APPROVED+RESOLVED，REJECT写REJECTED+CLOSED；只改变审核记录，**不创建任何余额、退款或账本交易**。未接入上游发送、项目消费或补偿付款。
- 每次写入的UUID requestId由调用方生成并保存；`owner + 调用身份(OWNER或确切客户) + requestId`唯一。同参数返回原回执，异参数拒绝；主密钥与JWT经营者共享OWNER命名空间，客户各自独立，不能查询其他身份的回执。
- 写入响应为`{requestId,ticketId,action,version,createdAt,notice}`。响应丢失后先GET原请求；仅在查无回执后，才可显式重试**同编号、同参数、同版本**。版本过期应读取最新内容并重新确认，不自动换号/升级版本。工单与回复/审核、请求回执一起回滚。
- 客户READ_ONLY只能读自己的工单，SUPPORT可提单/回复自己的工单；不能审单、操作资金或访问同项目其他客户。轮换/撤销/过期/暂停立即阻断旧凭据，写入事务持有owner锁后重新检查。关闭功能时JWT本人仍可查询既有记录；不再接受写入。


### 26.1 可选私有图片（027）

`imageData`仅接受`data:image/png;base64,...`或`data:image/jpeg;base64,...`，可省略。每次提单/回复最多一张；原文件≤2MiB、每边≤4096、总像素≤400万。服务端核实格式并去除源元数据，复制像素重新编码PNG后加密保存（输出≤4MiB），不保留原文件名。SVG/GIF/HTML/外部URL/伪造类型一律拒绝。包含图片的完整JSON体≤3MiB，超限HTTP413且不进入业务写入。

工单/回复DTO只额外返回`imageId`，没有图片原文、公开URL或密文。`GET /project-client-tickets/images/{imageId}`使用JWT；外部地址`GET /external/projects/v1/tickets/images/{imageId}`必须恰好一个`X-Project-Key`。图片ID不是访问令牌；其他owner或同项目不同客户均不可读取，已暂停客户的密钥也不可读取。前端按需取Blob，关闭时释放临时URL，不用带密钥URL展示图片。

图片与工单、回复版本和请求回执一起提交。响应丢失仍只查原UUID；重试须保持同一图文内容，换图不能沿用编号。图片格式/加密/写入失败会回滚此次工单/回复，不遗留孤儿图片，不改变资金。此类附件仅属于本地下游售后，不自动发给上游；独立的上游图文工单需另行预览并明确确认，见第24节。


## 27. 项目用量与只读运营统计（代码已部署，默认关闭）

复用迁移021/025/026，无新增DDL。以下统计只读取本平台已有记录，不调用供应商、不刷新上游余额、不产生任何资金结算。本人范围从登录身份/主密钥派生，不接受客户端指定其他owner。所有成功响应 `Cache-Control: no-store`。

| 方法 | 路径 | 权限 / 行为 |
|------|------|------|
| GET | `/project-clients/usage` | 登录本人；安全动作、结果、客户、本地工单与已结算本地资金汇总 |
| GET | `/project-clients/usage/projects` | 登录本人；按项目分页的本地额度；page 1–10000、pageSize 1–50，前端固定20 |
| GET | `/external/projects/v1/usage`、`/external/projects/v1/usage/projects` | 恰好一个 `X-Project-Key` 主密钥；READ_ONLY/MANAGE均可读，客户密钥即使有SUPPORT也不可读经营者汇总 |
| GET | `/admin/project-reports/overview` | `api-provider:update` 与 `payment:reconcile` 双权限；角色名称不能替代权限 |

- `window.from` / `through` / `timezone` 明示 Asia/Shanghai 下的滚动24小时，**不是自然日“今日”**。窗口起点/终点均包含，未来时间的调用日志不计入。各响应内使用只读、可重复读事务；项目分页各自读取当前状态，不承诺跨页冻结快照。
- `calls` 返回累计/窗口调用与未完成数；`actions` 最多50类，按累计调用降序、动作名稳定排序，`moreActions`说明是否还有未展示分类。总数不因分类上限截断。日志为尽力记录：鉴权前拒绝、日志写入失败可能不在统计内；`OK`只表示本平台处理返回，**不是供应商下单成功，也不是计费凭证**。本次外部统计请求在计算完成后才记日志，因此该次结果不包含自身。
- 客户数、工单数仅指本地下游；补偿待审核数不代表已付款。项目额度按projectId分别返回活跃/暂停额度与剩余实付可退预算，不相加不同项目单位，不混同上游余额。普通返回无密钥、客户明细、图片、工单正文或请求内容。
- `localFunding`仅合计APPLIED的本地开户/充值/转回；`upstreamFunding`仅合计SUCCEEDED的项目开户与兑换，带初始额度开户计入实际支出。金额为精确CNY字符串，`netDebited = debited - returned`。含零额度开户的操作数不是销售单数；上游DISPATCHING/UNKNOWN另计为`unresolvedOperations`，不纳入已结算金额。两组金额不相加当作收入或利润。
- 全局项目/绑定数只反映本地发布配置和已有客户号的ACTIVE绑定，不证明上游实时可用或已验收。接口统一限流至每用户/IP每分钟12次（外部接口仍保留原有密钥/IP防护）。失败、残缺响应不展示伪零；刷新/切换视图/关闭页面取消过期读取，不缓存报告到浏览器存储。

用户从“客户与项目 API → 用量与统计”进入；管理页“运营统计”读取全局概览。经营者分页明细和资金流水见第28节（已实现，默认关闭）；本节不表示P07全部业务兼容，更不表示真实项目消费已实现。


## 28. 经营者明细与结算流水（已实现，默认关闭）

复用迁移021/025/026；没有新增DDL、供应商请求、账户刷新或资金写入。相关源码已入库，运行镜像存在对应类；业务仍默认关闭，不能据此宣称生产可用。

| 方法 | 路径 | 权限 / 行为 |
|------|------|------|
| GET | `/admin/project-reports/owners` | 双权限；经营者历史集合的搜索和分页，支持 `keyword` |
| GET | `/admin/project-reports/owners/{id}` | 双权限；该经营者的安全身份、滚动24小时用量、客户/工单计数及分账本资金概况 |
| GET | `/admin/project-reports/owners/{id}/accounts` | 双权限；按项目/账户编号分页，含冻结单价、已结算金额、可退预算和余额上次记录时间 |
| GET | `/admin/project-reports/owners/{id}/clients` | 双权限；分页及 `projectId`、`status=ACTIVE/SUSPENDED/CLOSED` 筛选 |
| GET | `/admin/project-reports/ledger` | 双权限；全部或指定 `ownerId` 的已结算流水 |
| GET | `/project-ledger` | JWT登录本人；身份派生查询范围，不能用查询参数或 `X-Project-Key` 指定其他经营者 |

“双权限”指真实 `api-provider:update` **且** `payment:reconcile`，在HTTP和服务层分别校验，并检查当前操作账号有效。历史经营者可以是 `ACTIVE`、`DISABLED` 或 `MISSING`；已移除身份用空用户名表示，不能冒充该身份访问其他业务。只列出有项目账户、客户或历史动作的经营者，不暴露整个用户目录；无历史的经营者明细返回404。所有投影不返回密码、API密钥、项目访问地址、工单正文或请求负载。

### 筛选与分页

- 分页参数统一 `page=1`、`pageSize=20`，范围1–10000和1–50。经营者按编号升序，账户/客户按项目和编号排序。`keyword` 最多100字、拒绝控制字符；`%`、`_`、`!` 按字面搜索，纯数字还可精确匹配经营者编号。
- 流水支持 `projectId`、`clientId`、`book=PROJECT_ACCOUNT/CUSTOMER_CREDIT`、`direction=DEBIT/CREDIT`、`fromDate`、`throughDate` 和 `keyword`；仅管理员接口接受 `ownerId`。`ALL`/省略枚举表示不限制。客户编号须为规范小写UUID，只能筛选客户额度，不能与项目账户类型组合；项目/经营者编号须为正整数。
- 日期为 `YYYY-MM-DD`、年份1000–9998，按北京时间的**结算时间**筛选，不是申请时间。开始日期00:00含边界；结束日期包含当天、以次日00:00为排他上界；倒置区间或非法枚举拒绝。流水关键词匹配项目名、操作编号或账户/客户编号。

### 返回语义与界面

`records/total/current/size` 为分页；流水另有 `totals`、`checkedAt`、`timezone=Asia/Shanghai`。每笔含 `book/id/ownerId/projectId/title/subjectId/action/direction/amount/units/unitPrice/subjectBalanceAfter/walletBalanceAfter/requestedAt/settledAt`。金额为两位精确字符串，额度/单价为最多六位小数字符串。账户余额仅为缓存观察；没有记录的余额、时间或钱包余额明确返回 `null`，不能补零。

- `PROJECT_ACCOUNT` 只读SUCCEEDED的PROVISION/TOP_UP/WITHDRAW；`CUSTOMER_CREDIT`只读APPLIED的OPEN/TOP_UP/WITHDRAW。WITHDRAW为CREDIT，其余为DEBIT；零额度开户计入动作数但不是销售单数。UNKNOWN、DISPATCHING、READY、失败和过期记录不混入结算金额。
- 两账本通过UNION ALL保留，同一UUID跨账本仍是不同记录；行键为 `(book,id)`，排序为 `settledAt DESC, book ASC, id DESC`。`totals` 按账本给出全部匹配结果的笔数、扣款、转回、净扣款，不局限当前页；不混合项目单位，不把两组流量相加当收入或利润。
- 一次响应中的计数、分页及汇总使用只读可重复读快照；不同请求/翻页不承诺共享快照。成功响应no-store，沿用每用户/IP每分钟12次报告限流。
- 管理页新增“经营者明细”和“资金流水”，可从账户/客户钻取对应记录；本人从“客户与项目 API → 资金流水”进入。编辑筛选不自动请求，分页沿用已应用筛选；关闭/切换取消旧请求。失败或残缺响应不显示伪零，数据不写浏览器存储；支持手机、深色及精确长金额。

本轮没有新增外部密钥的流水HTTP端点，也不提供PHP action兼容层。客户额度仍未连接真实项目购买/消费；经营者明细与资金查询不能替代该缺口。
