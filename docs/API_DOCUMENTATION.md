# API 接口文档

> 更新时间：2026-09-06
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
| 匿名入口 | 仅 `/auth/login`、`/auth/refresh`、`/auth/mfa/verify`、`/register`、`/register/validate-invite-code`、健康/支付回调/只读主题/文档等明确路径；`auth/current` 和 `auth/logout` 必须认证 |

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

前端封装：`frontend/src/api/order.js`

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
| GET | `/{pluginId}/providers/{providerId}/catalog` | P01 闪电报价（project=sdxy/ydsjxy/xbd）、P03 黑鲨商品、P04 极光商品、P10 无心报价 |
| GET | `/{pluginId}/providers/{providerId}/schools` | 仅 P04 极光学校检索 |

目录/学校请求必须使用**已验证且已启用**的保存配置。学校和配置列表支持 `page=1…10000`、`pageSize=1…100`、`keyword` 至多 80 字符。Flash 一次请求只读取一个白名单项目；其他类型拒绝非空 project。商品金额 `unitPrice` 以十进制字符串返回；学校只返回 `items/page/pageSize/hasMore`，不虚构 total。

页面：`/admin/plugin-integrations`。运行步骤、协议形状与边界见 [PLUGIN_INTEGRATIONS.md](./PLUGIN_INTEGRATIONS.md)。


## 22. 原生服务商城与订单（代码已部署，默认关闭）

`NATIVE_SERVICES_ENABLED` 默认为 `false`；须经授权依次执行迁移 018 / 019 / 020 / 021 / 022 / 023 / 024 / 025 / 026 后启用。当前生产已部署接口代码，但尚未执行这些迁移且开关保持关闭，因此不代表业务已可调用。均需登录，返回 `Cache-Control: no-store`；用户只能访问所属订单，金额均为十进制字符串。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/services` | page / pageSize，支持 providerType 分类，先过滤后分页 |
| POST | `/services/{id}/lookup` | 授权账号查询，表单字段白名单；不返回密码/授权码 |
| GET | `/services/{id}/schools` | 极光及实习平台学校检索；实习支持 page、keyword，分页不透传路由元数据 |
| POST | `/services/{id}/quotes` | quantity、distance、fields、taskTimes、authorizedAccount、schedule（实习）；生成五分钟报价，不扣款 |
| GET | `/service-orders`、`/service-orders/{id}` | 本人订单与可用操作；含版本号，不含供应商凭据。Flash 未知 completed、实习 completed 返回 null，不等于 0；实习含 schedule / quantityUnit |
| POST | `/service-orders/{id}/sync` | 精确核对上游单号，读取进度；不自动凭状态退款 |
| GET | `/service-orders/{id}/options` | 无心或实习当前计划与可选值；实习额外返回 paidDates，不返回密码/账号；未返回项目不填默认值 |
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

五类业务表单和已知缺口、资金状态机及上线前置条件见 [PLUGIN_INTEGRATIONS.md](./PLUGIN_INTEGRATIONS.md)。

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

## 23. 原生项目中心与子钱包（代码已部署，默认关闭）

依赖迁移 021 及 `NATIVE_SERVICES_ENABLED`。以下端点全部要求登录，成功响应 `Cache-Control: no-store`，金额/额度返回十进制字符串。普通用户只能操作本人账户；上游客户密钥不出现在任何公共 DTO 中。

| 方法 | 路径 | 参数 / 行为 |
|------|------|------|
| GET | `/service-projects` | page/pageSize；开放项目及本人已有账户，下架不隐藏历史账户 |
| POST | `/service-projects/{id}/quotes` | `{action: PROVISION/TOP_UP/WITHDRAW, units, confirmedPolicy:true}`；开户 units 留空且初始余额固定零 |
| POST | `/project-accounts/{id}/refresh` | 显式查询并校验本人上游余额，不自动执行兑换 |
| GET | `/project-operations`、`/project-operations/{id}` | 本人记录/原操作状态；不会重发请求 |
| POST | `/project-operations/{id}/confirm` | 固定报价编号，至多派发一次；不接收客户端金额或上游客户号 |
| GET | `/admin/service-project-catalog` | providerId；接口管理权限，仅只读目录 |
| GET/POST | `/admin/service-projects` | 管理列表/发布项目；`api-provider:update` |
| PUT | `/admin/service-projects/{id}` | 带 version 更新；供应商/上游项目绑定不可替换 |
| GET | `/admin/project-operations`、`/admin/project-operations/{id}` | 双权限资金核对列表/详情 |
| POST | `/admin/project-operations/{id}/resolve` | `{outcome:ACCEPTED/NOT_ACCEPTED, customerId, evidence, upstreamChecked:true}`；已受理开户才允许填写经核实的 customerId |

发布字段为 providerId、remoteProjectId、title、description、unitPrice、unitCost、validUntil、evidence、upstreamChecked、enabled、version。管理员须核实实际单位成本，售价不低于成本，有效期不超过九十天。目录价不能证明最终成本。每个账户冻结开户费率；充值向上取整到分，转回向下取整到分，并受本平台未退充值额度与付款预算约束。转回不计入新充值总额。

开户/资金操作状态：READY、DISPATCHING、UNKNOWN、SUCCEEDED、NOT_ACCEPTED、EXPIRED。用户账户状态：NEW、ACTIVE、DISABLED、BUSY、UNKNOWN。重复确认返回原状态；失联不自动退款。核对要求 `api-provider:update` 与 `payment:reconcile` 同时存在，并提供至少十字的依据。接口主密钥或地址变化会阻断旧账户，不能借此替换上游客户。相同规范化地址和主密钥的重复接口配置不能重复绑定同一客户或工单。金额数值不经过 double；异常指数在展开/格式化之前被拒绝。人工核对保留当前上游明确的停用状态。

## 24. 项目文字工单与补偿审核（代码已部署，默认关闭）

依赖迁移 022。只访问本平台已绑定的本人项目工单，不转发上游宽范围列表，不允许用户填写任意上游工单号。所有写请求无自动重试，正文与审核记录在服务端加密保存；附件只返回“存在附件”标记，不返回 URL 或图片数据。

| 方法 | 路径 | 参数 / 行为 |
|------|------|------|
| GET | `/project-tickets`、`/project-tickets/{id}` | 本地列表/详情；page/pageSize、可选 accountId；不会自动访问上游 |
| POST | `/project-accounts/{id}/tickets` | `{type:suggestion/bug/compensation,title,description,compensationAmount,confirmedPolicy:true}`；仅生成本地草稿与操作编号 |
| POST | `/project-tickets/{id}/reply-quotes` | `{content,version,confirmedPolicy:true}`；本人回复预览 |
| POST | `/project-tickets/{id}/refresh` | 显式读取原绑定上游工单，不会按回复相似度解决 UNKNOWN |
| GET | `/project-ticket-operations/{id}` | 查询原操作；普通用户不能读取尚未确认的管理员审核说明 |
| POST | `/project-ticket-operations/{id}/confirm` | 原草稿编号，确认发送本人提交/回复，最多一次 |
| GET | `/admin/project-tickets`、`/admin/project-tickets/{id}` | 接口管理权限；仅本平台已绑定工单 |
| POST | `/admin/project-tickets/{id}/refresh` | 接口管理权限，显式更新上游回执 |
| POST | `/admin/project-tickets/{id}/review-quotes` | 双权限；`{result:approved/rejected,note,version,upstreamChecked:true}` |
| GET | `/admin/project-ticket-operations/{id}` | 管理查询原操作 |
| POST | `/admin/project-ticket-operations/{id}/confirm` | 双权限，只有原审核人可发送自己的 REVIEW 草稿 |
| POST | `/admin/project-ticket-operations/{id}/resolve` | 双权限；`{outcome:ACCEPTED/NOT_ACCEPTED,evidence,upstreamChecked:true}`；只核对原请求，不重发 |

草稿十分钟有效。重复确认不重复发送；过期、供应商配置变化或快照更新阻断旧预览。补偿申请额度大于零且最多六位小数；非补偿类型不得携带非零额度。标题至多 120 字、正文/回复至多 4000 字。上游响应受 256 KiB、单文本 8192 字符和 100 条回复限制，超过限制不会截断后伪称成功；更多回复与附件仍需上游有界协议。

**审核通过不等于付款。** REVIEW 不增加平台余额、项目充值预算或账本流水。未知的新工单缺少可独立验证的客户归属，禁止 ACCEPTED 认领；只有已经绑定的回复/审核可在真实证据和只读回执核对后完成本地结算。完整附件、安全上游项目登录及真实上游密钥生命周期仍未实现；独立的本平台项目密钥与原生 OpenAPI 见第25节。


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

依赖迁移026及025，不复用上游 `project-tickets`。JWT网页前缀 `/project-client-tickets`；`X-Project-Key`外部调用前缀 `/external/projects/v1/tickets`，后缀与请求体一致。

| 方法 | 后缀 | 行为 |
|------|------|------|
| GET | 空 | 本人列表，clientId/status/kind可选，page/pageSize默认1/20，最多100条；客户密钥强制限定自身clientId |
| GET | `/{id}` | 工单、申请金额、状态、版本、公开审核结论，不含owner私有费率或密钥 |
| GET | `/{id}/replies` | 纯文本沟通记录，按版本升序分页，author为OWNER/CUSTOMER |
| GET | `/by-request/{requestId}` | 仅查询当前调用身份的不可变请求回执；无记录返回null，不产生工单 |
| POST | 空 | `{requestId,clientId,kind,title,description,requestedAmount,consent:true}` 新建 |
| POST | `/{id}/replies` | `{requestId,version,content,consent:true}` 回复当前版本 |
| POST | `/{id}/decision` | `{requestId,version,action,note,consent:true}` 经营者处理；仅JWT本人/MANAGE主密钥 |

- kind为SUGGESTION/BUG/COMPENSATION；标题1…120字，描述/回复1…5000字，审核说明1…2000字，均为纯文本。requestedAmount为非负十进制金额，最多两位小数、99999999.99，仅补偿申请可非零；其余传`"0"`。金额只是申请参考，不是支付指令。附件与账号凭据禁止混入正文。
- 状态OPEN→IN_PROGRESS（回复）；普通工单action为RESOLVE/CLOSE，补偿申请为APPROVE/REJECT，结束后不可继续回复/处理。APPROVE写APPROVED+RESOLVED，REJECT写REJECTED+CLOSED；只改变审核记录，**不创建任何余额、退款或账本交易**。未接入上游发送、项目消费或补偿付款。
- 每次写入的UUID requestId由调用方生成并保存；`owner + 调用身份(OWNER或确切客户) + requestId`唯一。同参数返回原回执，异参数拒绝；主密钥与JWT经营者共享OWNER命名空间，客户各自独立，不能查询其他身份的回执。
- 写入响应为`{requestId,ticketId,action,version,createdAt,notice}`。响应丢失后先GET原请求；仅在查无回执后，才可显式重试**同编号、同参数、同版本**。版本过期应读取最新内容并重新确认，不自动换号/升级版本。工单与回复/审核、请求回执一起回滚。
- 客户READ_ONLY只能读自己的工单，SUPPORT可提单/回复自己的工单；不能审单、操作资金或访问同项目其他客户。轮换/撤销/过期/暂停立即阻断旧凭据，写入事务持有owner锁后重新检查。关闭功能时JWT本人仍可查询既有记录；不再接受写入。
