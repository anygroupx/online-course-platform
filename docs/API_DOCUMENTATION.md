# API 接口文档

> 更新时间：2026-07-12
> Base Path：`/api`
> 开发：`http://localhost:8080/api`
> Docker/生产容器：`http://localhost:8082/api`
> 公网：`https://course.example.com/api`
> OpenAPI UI（开发）：`/doc.html`（Knife4j，生产默认关闭）

## 通用约定

### 响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

> 部分历史接口可能使用 `code: 1` 表示成功，联调时以实际返回与 `Result` / `ResultCode` 为准。

### 鉴权

| 类型 | 说明 |
|------|------|
| JWT | Header：`Authorization: Bearer <access_token>` |
| API Key | 外部接口 `/external/**` 使用用户 API Key |
| 白名单 | `/auth/**`、`/register/**`、`/health`、`/ping`、`/payment/notify`、`/payment/return`、`/external/**`、文档路径等 |

### 角色

- `ADMIN`：管理员接口（`/admin/**` 等）
- `CS`：客服相关能力（视具体接口校验）
- `USER`：普通用户 / 代理

---

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
| API Key | `POST /api-keys/enable` | 开通 API 密钥 |

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
| CRUD | `/admin/api-providers` | API 提供商配置 |
| POST | `/admin/docking/import-platforms` | 一键导入平台 |
| POST | `/admin/docking/batch-sync` | 批量同步订单进度 |

详见 [batch-sync-usage.md](./batch-sync-usage.md)。

参数说明：

- `import-platforms`：`apiProviderId`、`priceMultiplier`、`targetCategoryId`
- `batch-sync`：`apiProviderId`、`timestampSeconds`、`offset`

> 请求需带 JWT，完整 URL 含 `/api` 前缀。

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

`SportRunController` 已挂载路由，部分方法仍为 TODO 占位，调用前请确认实现状态。

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
