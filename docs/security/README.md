# 安全运维文档（P2）

| 文档 | 说明 |
|------|------|
| [KEY_ROTATION.md](./KEY_ROTATION.md) | 密钥轮换 |
| [BACKUP_RECOVERY.md](./BACKUP_RECOVERY.md) | 备份恢复 RPO/RTO |
| [INCIDENT_RESPONSE.md](./INCIDENT_RESPONSE.md) | 安全事件响应 |
| [ASVS_5_CHECKLIST.md](./ASVS_5_CHECKLIST.md) | ASVS 5.0 验收清单 |
| [../SECURITY_ROADMAP.md](../SECURITY_ROADMAP.md) | 路线图 |
| [../SECURITY_FIXES.md](../SECURITY_FIXES.md) | 已落地修复记录 |

## 脚本
- `scripts/security/run-local-scans.sh` 本地扫描
- `scripts/security/rotate-secrets-checklist.sh` 轮换清单

## 迁移
- `database/migrations/008_p2_security.sql`
