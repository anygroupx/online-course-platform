# 安全事件响应 Runbook

## 严重级别
| 级别 | 示例 | 响应时限 |
|------|------|----------|
| CRITICAL | 支付私钥泄露、未授权入账、DB 公网暴露 | 15 分钟内遏制 |
| WARN | 暴力破解、签名失败激增、对账不一致 | 2 小时内排查 |
| INFO | 正常登录/密钥轮换记录 | 日审 |

## 信号来源
- `security_audit_log`（LOGIN_FAIL / ACCESS_DENIED / PAYMENT_CALLBACK / KEY_CHANGE / MFA_* / RECONCILE）
- `SecurityAlertNotifier` Webhook
- 支付日终对账 `payment_reconcile_report.status=MISMATCH`
- CI 秘密扫描告警

## 标准流程（PICERL 简化）
1. **准备**：值班人、Webhook、只读从库账号、断网/限流开关
2. **识别**：确认事件类型、影响用户/金额/时间窗
3. **遏制**：
   - 关闭公网/收紧防火墙
   - 轮换 JWT / DB / 支付密钥 / API Key
   - 撤销 refresh_token / 禁用可疑账号
   - 必要时关闭支付与外部 API
4. **根除**：修漏洞、下线后门、补授权与限流
5. **恢复**：验证对账与核心流程后恢复流量
6. **总结**：24h 内写复盘，更新 ASVS 清单与测试

## 支付异常专项
1. 立即核对 `payment_order` 与 `account_ledger`
2. 手工触发 `/admin/security/reconcile`
3. 对多入账订单冻结余额并人工冲正
4. 检查回调 IP 与签名失败审计

## 联系人模板
- 业务负责人：
- 研发值班：
- 运维/网络：
- 支付渠道客服：
