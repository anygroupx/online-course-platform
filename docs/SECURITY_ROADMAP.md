# 安全建设路线图

> 更新时间：2026-07-12
> P0/P1 已完成；P2 本轮已落地核心能力，持续项见未勾选项。


## 1. 工程化扫描
- [x] CI 接入 gitleaks（秘密扫描）— `.github/workflows/security-scan.yml`
- [x] CI 接入 OSV / npm audit（SCA）
- [x] CI 接入 Semgrep（SAST）
- [x] 容器/文件系统扫描（Trivy）
- [x] 可选预发 DAST（ZAP baseline，需配置 `DAST_TARGET_URL`）
- [x] 本地扫描脚本 `scripts/security/run-local-scans.sh`

## 2. 身份与访问
- [x] 管理员 TOTP MFA + 备用恢复码
- [ ] 完整 RBAC 权限表（资源-动作）替代单 role 字段（P1 已有角色，细粒度持续）
- [ ] 会话并发限制与设备管理
- [ ] 前端 Token 存储方案评估（HttpOnly Cookie + CSRF）
- [ ] 管理员 WebAuthn

## 3. 资金与支付
- [x] 支付日终对账任务（本地订单 vs 账本）
- [x] 对账异常审计/告警
- [ ] 退款流程状态机与幂等
- [ ] 对接支付宝官方账单文件做外部对账

## 4. 可观测与响应
- [x] 集中式审计日志（登录失败、权限拒绝、支付回调、密钥变更、MFA、对账）
- [x] 告警通道（Webhook + 结构化日志）
- [x] 安全事件响应 runbook（`docs/security/INCIDENT_RESPONSE.md`）
- [x] 备份恢复文档（`docs/security/BACKUP_RECOVERY.md`）
- [ ] 备份恢复实战演练记录归档

## 5. 传输与前端安全
- [x] 后端安全响应头兜底（CSP/HSTS/XFO 等）
- [ ] 持续收紧 CSP（去掉前端 unsafe-inline）
- [ ] 子资源完整性（SRI）
- [ ] 管理端独立域名与更严格 CORS

## 6. 验收
- [x] 按 OWASP ASVS 5.0 建立检查清单（`docs/security/ASVS_5_CHECKLIST.md`）
- [x] Controller BOLA/BFLA 自动化抽样（`BolaBflaAuthorizationTest`）
- [ ] 全量 Controller 矩阵与渗透测试闭环

## 7. 密钥管理
- [x] 密钥轮换流程与清单（`docs/security/KEY_ROTATION.md`）
- [x] API Key 哈希/作用域/过期（P1）
- [ ] 以 KMS/Vault 替换本地 `SecretCrypto`
- [ ] 密钥版本化与自动化轮换作业
