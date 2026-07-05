# Uptime Kuma 监控配置指南

## 添加课程平台监控

在 Uptime Kuma (http://10.0.0.2:3001 或 https://status.csuft.codes) 中添加以下监控项：

### 1. 前端页面监控（公网访问）

- **监控类型**: HTTP(s)
- **显示名称**: 课程平台 - 前端
- **URL**: `https://course.example.com`
- **心跳间隔**: 60 秒
- **重试次数**: 3
- **预期状态码**: 200

### 2. 后端 API 健康检查（公网）

- **监控类型**: HTTP(s)
- **显示名称**: 课程平台 - API 健康检查
- **URL**: `https://course.example.com/api/health`
- **心跳间隔**: 60 秒
- **重试次数**: 3
- **预期状态码**: 200
- **关键词检查**: `status":"UP"` (JSON 响应中包含此字符串)

### 3. 后端内部监控（VPN内网）

- **监控类型**: HTTP(s)
- **显示名称**: 课程平台 - 后端内部
- **URL**: `http://10.0.0.2:8082/api/health`
- **心跳间隔**: 30 秒
- **重试次数**: 2
- **预期状态码**: 200

### 4. 前端容器监控（VPN内网）

- **监控类型**: HTTP(s)
- **显示名称**: 课程平台 - 前端容器
- **URL**: `http://10.0.0.2:8888/health`
- **心跳间隔**: 30 秒
- **重试次数**: 2
- **预期状态码**: 200

### 5. MySQL 数据库监控（端口检查）

- **监控类型**: Port
- **显示名称**: 课程平台 - MySQL
- **主机名**: `10.0.0.2`
- **端口**: `13306`
- **心跳间隔**: 60 秒

### 6. Redis 监控（端口检查）

- **监控类型**: Port
- **显示名称**: 课程平台 - Redis
- **主机名**: `10.0.0.2`
- **端口**: `6379`
- **心跳间隔**: 60 秒

---

## 分组建议

在 Uptime Kuma 中创建一个"课程平台"组，将以上所有监控项添加到该组。

---

## 通知配置

### 1. 邮件通知（可选）

在 Uptime Kuma 设置中配置 SMTP：
- 如发生故障，发送邮件通知
- 建议监控"后端 API 健康检查"和"前端页面监控"

### 2. 状态页面（可选）

创建一个公开的状态页面：
- 访问 Uptime Kuma → Status Pages
- 创建新页面：`course-platform-status`
- 添加上述监控项
- 可以通过 `https://status.csuft.codes/status/course-platform` 访问（需配置VPS Nginx）

---

## Docker 健康检查说明

Docker Compose 中已配置容器级别的健康检查：

### 后端健康检查

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8082/api/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

- 每30秒检查一次
- 启动后60秒才开始健康检查
- 失败3次后标记为不健康

### 前端健康检查

```yaml
healthcheck:
  test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 10s
```

### MySQL 健康检查

```yaml
healthcheck:
  test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 30s
```

### Redis 健康检查

```yaml
healthcheck:
  test: ["CMD", "redis-cli", "ping"]
  interval: 10s
  timeout: 5s
  retries: 5
```

---

## 查看 Docker 健康状态

```bash
# 查看所有容器健康状态
docker compose ps

# 查看特定容器的健康检查日志
docker inspect --format='{{json .State.Health}}' course-backend | jq

# 实时监控健康状态
watch -n 2 'docker compose ps'
```

---

## 告警阈值建议

- **前端页面**: 连续3次失败（3分钟）→ 发送告警
- **后端API**: 连续2次失败（2分钟）→ 发送告警
- **数据库**: 连续5次失败（5分钟）→ 发送紧急告警
- **Redis**: 连续5次失败（5分钟）→ 发送警告

---

最后更新: 2025-12-09
