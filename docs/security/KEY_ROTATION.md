# 密钥轮换流程

## 范围
- `JWT_SECRET`
- `APP_CRYPTO_SECRET`（本地 AES，生产应迁 KMS）
- MySQL / Redis 密码
- 支付宝应用私钥 / 支付宝公钥
- 用户 API Key
- 管理员 MFA 密钥与备用码

## 原则
1. **先轮换后失效**：能双写则双写，不能双写则接受短时会话失效。
2. **禁止明文入库/入仓**：密钥仅存在于密钥管理或运行时环境变量。
3. **可审计**：轮换动作写入 `security_audit_log`（`KEY_CHANGE`）。
4. **可回滚**：保留上一版本密钥至少 1 个发布窗口（KMS 版本化）。

## 操作步骤（摘要）
见 `scripts/security/rotate-secrets-checklist.sh`。

### JWT
1. 生成新密钥：`openssl rand -base64 48`
2. 滚动更新全部实例环境变量
3. 可选：清空/撤销 refresh_token 表强制重登

### APP_CRYPTO_SECRET
1. 使用旧密钥解密敏感字段
2. 用新密钥重加密后写回
3. 切换应用密钥并验证支付配置读取

### API Key
- 已开通用户在个人中心选择「轮换 APIKey」，或使用网页登录 JWT 调用 `POST /api/api-keys/rotate`，JSON 体为 `{"currentPassword":"当前登录密码"}`。
- 轮换免费，只能操作自己；需验证当前密码。旧密钥立即失效，新密钥有效期一年，保留原作用域。
- 响应 `Cache-Control: no-store`，仅返回一次明文；库中仅存 hash/prefix/scopes/expire。立即安全保存并更新第三方配置。
- 丢失密钥不可从哈希恢复，不得重新开放资料接口读取明文。详见 [第三方API说明](../EXTERNAL_API_GUIDE.md)。

### 支付密钥
1. 支付宝平台创建新密钥对
2. 后台更新配置（加密存储）
3. 用沙箱/小额订单验证回调验签

## 周期建议
| 类型 | 建议周期 |
|------|----------|
| JWT | 90 天或人员变动后立即 |
| DB/Redis | 90 天 |
| 支付私钥 | 180 天或怀疑泄露立即 |
| API Key | 用户主动/泄露立即 |
| MFA backup | 使用后立即作废并重生 |
