# 批量同步功能使用指南

> 更新时间：2026-07-12

## 功能说明

对接第三方（如 Benz）提供商时：

1. **一键导入平台/课程**
2. **批量同步订单进度**（支持增量时间戳）

相关后端：`PlatformDockingController`（`/admin/docking`）

## 数据库

确保已执行迁移：

```bash
mysql -u root -p online_course < database/migrations/001_add_last_sync_time.sql
```

字段：`api_provider.last_sync_time`（秒级时间戳）。

## 接口

> 以下路径均需加 context-path：`/api`
> 开发示例主机：`http://localhost:8080`
> Docker 示例主机：`http://localhost:8082`
> 均需管理员 JWT：`Authorization: Bearer <token>`

### 1. 一键导入平台

`POST /api/admin/docking/import-platforms`

| 参数 | 必填 | 说明 |
|------|------|------|
| apiProviderId | 是 | API 配置 ID |
| priceMultiplier | 否 | 价格倍率，默认 1.0 |
| targetCategoryId | 否 | 目标分类 ID |

```bash
curl -X POST "http://localhost:8080/api/admin/docking/import-platforms?apiProviderId=1&priceMultiplier=1.3" \
  -H "Authorization: Bearer <token>"
```

### 2. 批量同步订单进度

`POST /api/admin/docking/batch-sync`

| 参数 | 必填 | 说明 |
|------|------|------|
| apiProviderId | 是 | API 配置 ID |
| timestampSeconds | 否 | 起始时间戳；空则用上次同步时间 |
| offset | 否 | 分页偏移，默认 0 |

```bash
# 增量
curl -X POST "http://localhost:8080/api/admin/docking/batch-sync?apiProviderId=1" \
  -H "Authorization: Bearer <token>"

# 指定时间
curl -X POST "http://localhost:8080/api/admin/docking/batch-sync?apiProviderId=1&timestampSeconds=1640000000" \
  -H "Authorization: Bearer <token>"

# 分页
curl -X POST "http://localhost:8080/api/admin/docking/batch-sync?apiProviderId=1&offset=10000" \
  -H "Authorization: Bearer <token>"
```

### 返回字段（示例）

```json
{
  "code": 200,
  "message": "同步完成",
  "data": {
    "syncedCount": 150,
    "updatedCount": 145,
    "notFoundCount": 5,
    "timestamp": 1640001234
  }
}
```

## 注意

- 批量同步当前以 Benz 类对接为主，其他平台请确认实现是否覆盖
- 导入时注意价格倍率，避免重复导入造成脏数据
- 生产环境请在低峰执行大批量同步

实现纪要（历史）见 [archive/batch-sync-implementation.md](./archive/batch-sync-implementation.md)。
