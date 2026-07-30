#!/usr/bin/env bash
# 密钥轮换操作清单（打印步骤，不自动改生产）
set -euo pipefail
cat <<'TXT'
=== 密钥/凭据轮换检查清单 ===
1. JWT_SECRET：生成 >=32 字节随机串，滚动更新应用后旧 Token 自然失效
2. APP_CRYPTO_SECRET：先用双写/重加密脚本处理支付私钥与第三方密码，再切换主密钥
3. MYSQL/REDIS 密码：先改库侧，再滚动更新应用环境变量
4. 支付宝私钥/公钥：在支付宝开放平台轮换，更新 payment_config 加密字段
5. API Key：调用管理端/用户端轮换接口，旧 key 立即失效
6. Refresh Token：可批量 revoked_at=now() 强制全员重登
7. MFA backup codes：管理员在 /auth/mfa 关闭并重新绑定
8. 更新 .env / 密钥管理系统，禁止回写明文到 Git
9. 记录 security_audit_log 事件 KEY_CHANGE
10. 验证：登录、支付回调、外部 API、MFA 登录
TXT
