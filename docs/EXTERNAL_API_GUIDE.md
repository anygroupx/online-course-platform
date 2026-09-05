# 第三方 API 使用说明与安全兼容性

本说明对应网页 `/api-guide`。基础路径为 `/api`；它与管理员配置上游的 `/api/admin/api-providers` 是两套不同接口。

## 1. 认证和请求格式

- **全部业务接口使用 POST**，完整地址是 `https://你的站点/api/external/接口名`。若基础 URL 已有 `/api`，不要再拼接一次。
- 认证字段为 `uid`（个人中心的 UUID v4）和 **完整** `api_key`。
- 兼容原有 PHP/29 对接参数 `key`。二选一；同时传不同值会被拒绝。`apiKey` 不是支持的别名。
- 使用 `application/x-www-form-urlencoded` **请求体**，不要把密钥、学生密码放在 URL，也不要发送 JSON 请求体。
- 对外接口不需要 JWT、Refresh Token、Cookie 或网页登录。第三方调用不因网页会话过期而失效，调用失败也不应刷新 JWT。
- 生产必须使用 HTTPS。浏览器跨域调用还需将精确 Origin 加入 `course.security.allowed-origins`；服务端对接不受浏览器 CORS 限制。允许的跨域响应暴露 `Retry-After`。

```bash
# 示例变量由部署方安全注入；不要将真实凭证提交到仓库。
BASE_URL='https://course.example.com/api'
curl --request POST "${BASE_URL%/}/external/getmoney" \
  --data-urlencode "uid=${COURSE_UID}" \
  --data-urlencode "api_key=${COURSE_API_KEY}"
```

```javascript
const response = await fetch('https://course.example.com/api/external/getmoney', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ uid: 'YOUR_UUID', api_key: 'YOUR_API_KEY' }),
});
const result = await response.json();
if (!response.ok || result.code !== 1) throw new Error(result.message);
console.log(result.data.money);
```

网页文档同时提供每个接口的 cURL、JavaScript、Python、PHP/29 示例。示例始终保留占位符，不嵌入在线测试框中的真实密钥。

## 2. 接口清单

所有字段都是表单字符串。下面的参数不重复列出必填的 `uid`、`api_key`（或 `key`）。

| 路径（相对于 `/api`） | 作用域 | 必填业务参数 | 可选参数 | 返回 `data` |
|---|---|---|---|---|
| `/external/getmoney` | `balance:read` | 无 | 无 | `{money: 金额}` |
| `/external/get-platforms` | `platforms:read` | 无 | 无 | `[{id,name,description,price}]`，仅启用的平台 |
| `/external/query-courses` | `platforms:read` | `platform,user,pass` | `school` | `{studentName,studentAccount,schoolName,courses:[{id,name,description,endTime,selected}],message}` |
| `/external/add` | `orders:write` | `platform,user,pass,kcname` | `school,kcid` | `{orderNo}` |
| `/external/chadan` | `orders:read` | `username` | 无 | 自己的订单列表，字段见下文 |
| `/external/query-progress` | `orders:read` | `orderNo` | 无 | 本地已同步的订单进度详情 |
| `/external/budan` | `orders:write` | `orderNo` | 无 | `{orderNo}` |

注意：
- `platform` 是平台列表中的正整数 ID；`kcid` 是查课结果中的课程 ID，不是平台 ID。
- `user` 是学生账号，`pass` 是学生密码；**查单接口单独使用 `username`**。
- 平台列表 `price` 为基础价格，实际扣费遵循当前用户费率/特价与账本规则。
- `orderNo` 是本平台订单编号，不能传数据库 ID 或上游订单编号。
- 查单、查进度、补单都绑定密钥所属用户，不接受客户端传入 `userId` 来切换身份，不返回学生密码。
- 查单返回字段：`orderNo,ptname,school,name,user,kcname,addtime,courseStartTime,courseEndTime,examStartTime,examEndTime,status,process,remarks`。未查询到订单时返回业务错误。
- 查进度返回字段：`orderNo,platformName,studentAccount,courseName,orderStatus,orderStatusText,dockStatus,progress,remarks,createTime,updateTime`。查询读取本地同步结果，并非每次实时请求上游。
- 补单每单最多 5 次，还需满足订单状态、间隔和上游业务规则。

## 3. 响应与失败处理

```json
{"code":1,"message":"查询成功","data":{"money":300.00}}
```

成功为 **HTTP 200 且 `code === 1`**，不是 `code:200`。通用消息字段是 `message`，不是 `msg`；余额字段是 `data.money`，不是 `data.balance`。旧平台适配器可以在自己的边界转换这些字段。

| HTTP | code | 含义 / 建议 |
|---|---|---|
| 400 / 422 | -2 | 缺参数、UUID/平台 ID 格式不正确、凭证别名冲突、请求体格式错误 |
| 405 | -2 | 方法不支持，使用 POST |
| 401 | -205 | UID/密钥组合不正确、密钥已过期或未迁移；前缀不能认证；不要刷新 JWT |
| 403 | -105 / -101 | 账号已禁用 / 缺少所需密钥作用域 |
| 429 | -109 | IP 或密钥限流，按 `Retry-After` 秒数退避 |
| 503 | -118 | Redis 安全限流暂不可用，按 `Retry-After` 等待；保持 fail-closed |
| 400 | -200 / -207 | 余额不足 / 订单不存在或不属于该用户 |
| 409 | -206 | 订单已存在，不要重复下单 |
| 400 / 502 | -1 / -503 | 业务或上游调用失败，保存 `message` 和 `errorId` 供排查 |

默认外部限流为 IP 180 次/分钟、密钥 120 次/分钟，部署方可通过 `app.security.rate-limit.external-ip/external-key` 调整。两个凭证别名进入同一密钥限流维度；切换别名不能绕过限流。

**写请求不能盲目重试**：下单、补单会产生真实业务效果。网络超时不代表服务端未处理，应先查单确认。在线工具不自动重试，并对下单/补单二次确认。

## 4. 密钥展示、轮换与旧客户端迁移

- 用户信息接口仅返回 `apiEnabled/apiKeyPrefix/apiKeyExpiresAt`；只有开通/轮换响应返回一次完整明文，响应为 `Cache-Control: no-store`。
- 个人中心签发弹窗支持长密钥换行、完整复制（包含 HTTP 环境的复制回退）。关闭或离开页面后清除组件明文。
- 忘记保存或到期：个人中心点击 **轮换 APIKey**，验证当前登录密码后免费轮换。旧密钥立即失效，新密钥有效期一年，**保留原有作用域，不恢复管理员撤销的权限**。
- 轮换接口：`POST /api/api-keys/rotate`，使用网页登录 JWT，JSON 请求体 `{"currentPassword":"当前登录密码"}`。只能操作自己，不能传目标 UID；需要先开通密钥，不可借轮换免除首次开通费用。限流保护覆盖开通和轮换，轮换同时受密码验证频率限制。
- 开通接口：`POST /api/api-keys/enable?type=1`；并发开通持有账户行锁，防止重复扣费/签发。
- 旧系统的数字 UID 必须更新为公开 UUID。`014_p0_secret_data_transition.sql` 将原有明文密钥转为哈希、补齐作用域/有效期并清除数据库明文；**客户端已经保存的原密钥仍然可用**，无需仅因哈希存储而更换。
- 不支持运行时回退读取数据库旧明文，也不恢复从个人资料接口读取完整密钥的旧行为。

网页在线测试不再读写 `api_test_key`，会清理历史残留；凭证只放当前页面内存，离开/刷新页面清除。已有的 API Key 生命周期独立于网页 JWT；注销网页会话不等于撤销第三方密钥。

## 5. 安全迭代兼容检查

| 安全措施 | 对合法第三方调用的影响 |
|---|---|
| JWT 内存化、Refresh Session 轮换 | 不参与 `/external/` 认证；多余 Bearer 头被忽略 |
| 管理端 RBAC 收紧 | APIKey 验证后只在当前请求建立拥有者身份，供补单等业务对象权限校验；不继承后台角色/全量订单权限。API Key 不能访问 `/user/info`、`/admin/**` 等内部接口 |
| API Key 哈希/到期/作用域 | 保留校验；正确迁移且未过期的旧密钥继续可用 |
| 原子账本与订单归属 | 保留；真实余额不足、重复订单、越权订单仍应失败 |
| Redis 反滥用 | 保留 IP + Key 限流和不可用时关闭放行；提供明确状态与退避时间 |
| 出站 SSRF、DNS、TLS、上游验证 | 不影响本地查余额/查单；查课和下单相关上游链路需已验证、启用且符合策略，不应关闭防护 |

管理员排障顺序：确认路径/编码 → UID/完整密钥 → 到期/作用域/账号状态 → 429/503 → 订单归属与业务约束 → 上游验证和出站策略。不要通过扩大匿名白名单、恢复明文密钥或禁用 SSRF/TLS 来“修复”调用。

测试与移动端检查记录见 [MOBILE_API_INTEGRATION_REPORT.md](./MOBILE_API_INTEGRATION_REPORT.md)。
