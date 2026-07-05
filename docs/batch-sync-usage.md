# 批量同步功能使用指南

## 📌 功能说明

学习 benz 一键对接的实现方式，为当前项目添加批量订单进度同步功能。

## 🔧 安装步骤

### 1. 执行数据库迁移

```sql
-- 在MySQL数据库中执行以下SQL
ALTER TABLE `api_provider`
ADD COLUMN `last_sync_time` BIGINT DEFAULT NULL COMMENT '上次同步时间戳（秒）' AFTER `balance`;
```

或直接执行迁移文件：
```bash
cd j:/29/online-course-platform/database/migrations
mysql -u root -p your_database < 001_add_last_sync_time.sql
```

### 2. 编译和运行项目

按照您的常规流程编译和运行后端服务。

## 🚀 使用方法

### 接口1：一键导入平台/课程（已优化）

**接口地址**: `POST /admin/docking/import-platforms`

**参数**:
- `apiProviderId`: API配置ID（必填）
- `priceMultiplier`: 价格倍率（可选，默认1.0）
- `targetCategoryId`: 目标分类ID（可选）

**示例**:
```bash
curl -X POST "http://localhost:8080/admin/docking/import-platforms?apiProviderId=1&priceMultiplier=1.3"
```

**说明**: 已修复价格倍率问题，现在导入的平台价格会正确应用倍率。

### 接口2：批量同步订单进度（新增）

**接口地址**: `POST /admin/docking/batch-sync`

**参数**:
- `apiProviderId`: API配置ID（必填）
- `timestampSeconds`: 时间戳（可选，为空则使用上次同步时间）
- `offset`: 分页偏移量（可选，默认0）

**示例**:
```bash
# 增量同步（自上次同步后更新的订单）
curl -X POST "http://localhost:8080/admin/docking/batch-sync?apiProviderId=1"

# 全量同步（从指定时间戳开始）
curl -X POST "http://localhost:8080/admin/docking/batch-sync?apiProviderId=1&timestampSeconds=1640000000"

# 分页同步
curl -X POST "http://localhost:8080/admin/docking/batch-sync?apiProviderId=1&offset=10000"
```

**返回示例**:
```json
{
  "code": 200,
  "msg": "同步完成",
  "data": {
    "syncedCount": 150,      // 接口返回的订单数
    "updatedCount": 145,     // 成功更新的订单数
    "notFoundCount": 5,      // 未找到匹配的订单数
    "timestamp": 1640001234  // 本次同步的时间戳
  }
}
```

## 📝 核心改进

1. **批量同步**: 参考 benz 的 benztb.php，一次接口调用获取多个订单更新
2. **增量同步**: 使用时间戳只获取有更新的订单，减少数据传输
3. **价格倍率**: 修复一键导入时价格倍率不生效的问题
4. **自动记录**: 自动保存最后同步时间戳，下次可直接增量同步

## ⚠️ 注意事项

- 批量同步功能目前仅支持 Benz (27) 平台
- 其他平台（如oligei、29、ikun等）保持原有的单个查询功能
- 建议首次使用时观察日志，确认接口调用正常
- 如遇到订单匹配问题，检查学生账号、密码、课程名称是否一致

## 🔍 故障排查

### 问题1：找不到匹配的订单
- 原因：第三方返回的学生账号、密码、课程名称与本地订单不匹配
- 解决：检查订单信息是否一致，特别注意大小写和空格

### 问题2：同步数量为0
- 原因1：时间戳太新，没有更新的订单
- 解决：传入更早的时间戳或传null进行全量同步
- 原因2：第三方接口未开启批量查询
- 解决：联系第三方确认接口是否支持 `/api.php?act=plchadan`

## 📚 相关文档

- 实施总结: `docs/batch-sync-implementation.md`
- 实现计划: `.gemini/antigravity/brain/*/implementation_plan.md`
- 任务追踪: `.gemini/antigravity/brain/*/task.md`
