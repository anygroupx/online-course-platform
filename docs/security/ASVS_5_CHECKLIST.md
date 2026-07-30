# OWASP ASVS 5.0 安全验收清单（裁剪）

> 状态：P2 已落地项标记为 Done；其余为持续项。
> 范围：在线网课管理平台（前后端分离 + 支付 + 外部 API）

## V1 架构与设计
- [x] 安全响应头（CSP/HSTS/XFO 等，Nginx + `SecurityHeadersFilter`）
- [x] 生产禁止 CORS `*`
- [x] 基础设施端口不直接映射公网（MySQL/Redis/ES）
- [ ] 威胁建模年度更新

## V2 认证
- [x] 密码哈希（BCrypt）
- [x] 默认弱密码强制改密
- [x] Refresh Token 哈希 + 撤销/重放家族吊销
- [x] 管理员 TOTP MFA + 备用码
- [x] 登录/注册/支付/外部 API 限流
- [ ] 管理员 WebAuthn / 硬件密钥
- [ ] 会话并发与设备管理

## V3 会话管理
- [x] JWT Access + Refresh 分离
- [x] 登出撤销 Refresh Token
- [ ] HttpOnly Cookie 方案评估落地

## V4 访问控制
- [x] 方法级 `@PreAuthorize` / 角色校验（ADMIN/CS/USER）
- [x] 客服会话所有权校验（P1）
- [x] BOLA/BFLA 自动化抽样测试
- [ ] 完整资源-动作 RBAC 表

## V5 输入校验
- [x] Bean Validation + 统一错误码/HTTP 状态
- [x] 全局异常 errorId
- [ ] 前端 CSP 去掉 unsafe-inline（持续）

## V6 密码学
- [x] 敏感字段 `SecretCrypto` 加密
- [x] API Key 仅存哈希
- [x] 密钥轮换文档与清单
- [ ] KMS/Vault 替换本地密钥

## V7 错误处理与日志
- [x] 集中式 `security_audit_log`
- [x] WARN/CRITICAL Webhook 告警
- [x] 事件响应 runbook
- [ ] SIEM 长期归档

## V8 数据保护
- [x] API 响应 VO 白名单/脱敏（P1）
- [x] 下线学生明文密码导出（P0）
- [x] 备份恢复文档
- [ ] 备份加密与异地演练自动化

## V9 通信
- [x] 生产 HTTPS + HSTS
- [x] 安全响应头兜底

## V10 恶意代码
- [x] CI 秘密扫描 / SCA / SAST / 镜像扫描工作流
- [ ] 供应链签名与依赖锁定强化

## V11 业务逻辑
- [x] 支付回调幂等与竞态修复（P0/P1）
- [x] 支付日终对账
- [x] 余额/流水原子更新（P1）
- [ ] 退款状态机

## V12 文件与资源
- [ ] 上传类型/大小/病毒扫描（若启用上传）

## V13 API 与 Web Service
- [x] 外部 API Key 哈希校验
- [x] 管理端接口授权
- [x] 限流
- [ ] 完整 OpenAPI 安全方案审计

## V14 配置
- [x] `.env.example` 无真实密钥
- [x] JWT 生产 fail-fast
- [ ] 配置基线自动巡检
