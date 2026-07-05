# Docker 部署文档

## 📋 概述

本文档描述如何使用 Docker 部署在线课程平台到生产环境。

### 架构说明

```
互联网用户
    ↓ HTTPS
VPS Nginx (192.0.2.10)
    ↓ SSL终止 + 反向代理
WireGuard VPN (10.0.0.0/24)
    ↓
本地开发机 Docker (10.0.0.2)
    ├── Frontend (端口 8888)
    ├── Backend (端口 8082)
    ├── MySQL (端口 13306)
    └── Redis (端口 6379)
```

## 🚀 快速部署

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- WireGuard VPN 已配置并连接
- 本地机器 IP: `10.0.0.2`

### 部署步骤

#### 1. 准备环境变量

```bash
# 复制环境变量示例文件
cp .env.example .env

# 编辑 .env 文件，配置实际密码
nano .env
```

必须修改的配置：
- `MYSQL_ROOT_PASSWORD`: MySQL root 密码
- `MYSQL_PASSWORD`: 应用数据库密码
- `JWT_SECRET`: JWT 密钥（256位）

#### 2. 创建 Docker 网络

```bash
# 创建 local_net 网络（如果不存在）
docker network create local_net
```

#### 3. 运行部署脚本

**Linux/Mac:**
```bash
chmod +x deploy.sh
./deploy.sh
```

**Windows:**
```cmd
deploy.bat
```

#### 4. 验证部署

```bash
# 检查容器状态
docker compose ps

# 查看日志
docker compose logs -f

# 测试后端健康
curl http://localhost:8082/api/health

# 测试前端
curl http://localhost:8888
```

## 🔧 手动部署

如果需要手动控制部署流程：

```bash
# 1. 构建镜像
docker compose build

# 2. 启动服务
docker compose up -d

# 3. 查看日志
docker compose logs -f backend
docker compose logs -f frontend

# 4. 停止服务
docker compose down

# 5. 完全清理（包括数据卷）
docker compose down -v
```

## 🌐 VPS Nginx 配置

### 1. 上传配置文件到 VPS

```bash
# 将配置文件上传到 VPS
scp deploy/vps-nginx-course.conf user@192.0.2.10:/tmp/
```

### 2. 在 VPS 上配置 Nginx

```bash
# SSH 登录到 VPS
ssh user@192.0.2.10

# 移动配置文件到 Nginx 目录
sudo mv /tmp/vps-nginx-course.conf /etc/nginx/sites-available/course.example.com.conf

# 创建软链接
sudo ln -s /etc/nginx/sites-available/course.example.com.conf /etc/nginx/sites-enabled/

# 测试配置
sudo nginx -t

# 重载 Nginx
sudo systemctl reload nginx
```

### 3. 配置 SSL 证书

```bash
# 使用 Certbot 获取 Let's Encrypt 证书
sudo certbot certonly --nginx -d course.example.com

# Certbot 会自动配置证书路径
```

## 🌐 同源代理部署方案（推荐）

### 为什么需要同源代理？

在生产环境中，如果前端访问 `https://tunnel.example.com:24772`，而后端API使用 `https://frp-dad.com:14255`，浏览器会触发 **CORS跨域限制**，即使配置了CORS也可能出现各种问题。

**同源代理方案**通过Nginx反向代理，让前后端使用同一个域名和端口，从根本上避免CORS问题。

### 架构说明

```
用户浏览器
    ↓ 访问 https://tunnel.example.com:24772
    ↓ 请求页面: GET /
    ↓ 请求API: POST /api/auth/login
FRP穿透 (外网 → 内网)
    ↓
内网 Nginx (192.168.1.10:8888)
    ├─ / → 前端静态文件 (Vue3 SPA)
    └─ /api → 反向代理到 http://127.0.0.1:14255
           ↓
    本地后端服务 (Spring Boot)
```

### 配置步骤

#### 1. 前端配置

前端使用**相对路径** `/api` 而不是完整域名：

**`.env.production`:**
```env
# ✅ 使用相对路径（推荐）
VITE_API_BASE_URL=/api

# ❌ 不要使用完整域名（会触发CORS）
# VITE_API_BASE_URL=https://frp-dad.com:14255/api
```

#### 2. 内网 Nginx 配置

在 `192.168.1.10` 的 8888 端口配置Nginx：

**配置文件位置:** `deploy/local-nginx-8888.conf`

```nginx
server {
    listen 8888;
    server_name localhost 192.168.1.10;
    
    # 前端静态文件目录
    root /var/www/online-course-platform/frontend/dist;
    index index.html;

    # API反向代理到后端 (关键配置)
    location /api/ {
        proxy_pass http://127.0.0.1:14255/api/;
        proxy_http_version 1.1;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # SPA路由支持
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**部署步骤:**
```bash
# 1. 复制配置文件到Nginx目录
sudo cp deploy/local-nginx-8888.conf /etc/nginx/sites-available/

# 2. 创建软链接
sudo ln -s /etc/nginx/sites-available/local-nginx-8888.conf /etc/nginx/sites-enabled/

# 3. 测试配置
sudo nginx -t

# 4. 重载Nginx
sudo systemctl reload nginx
```

#### 3. FRP 配置

FRP只需穿透内网 8888 端口到外网 `https://tunnel.example.com:24772`：

```ini
[web-course]
type = tcp
local_ip = 192.168.1.10
local_port = 8888
remote_port = 24772
```

#### 4. 验证部署

```bash
# 内网测试
curl http://192.168.1.10:8888           # 前端
curl http://192.168.1.10:8888/api/health  # 后端API

# 外网测试
curl https://tunnel.example.com:24772            # 前端
curl https://tunnel.example.com:24772/api/health   # 后端API
```

### 优势总结

✅ **零CORS问题**: 前后端同域名，浏览器不会触发跨域检查  
✅ **简化配置**: 不需要复杂的CORS配置和预检请求  
✅ **统一入口**: 所有请求走同一个域名，便于监控和管理  
✅ **生产级方案**: 业界标准做法，稳定可靠

## 📊 服务访问地址

### 本地访问（开发机）

- 前端: http://localhost:8888
- 后端 API: http://localhost:8082/api
- API 文档: http://localhost:8082/api/doc.html
- MySQL: localhost:13306
- Redis: localhost:6379

### 通过 VPN 访问（内网）

- 前端: http://10.0.0.2:8888
- 后端: http://10.0.0.2:8082/api

### 公网访问（推荐）

- 课程平台: https://course.example.com
- API 端点: https://course.example.com/api
- API 文档: https://course.example.com/api/doc.html

## 🔐 安全配置

### 1. 修改默认密码

编辑 `.env` 文件：

```env
MYSQL_ROOT_PASSWORD=your_strong_password_here
MYSQL_PASSWORD=another_strong_password
JWT_SECRET=your-very-long-random-256-bit-secret-key
```

### 2. 生产环境建议

```env
# 关闭 API 文档（生产环境）
API_DOC_ENABLED=false
```

### 3. 防火墙配置

确保只有 VPN 和本地可以访问 Docker 端口：

```bash
# 仅允许本地和 VPN 访问
sudo ufw allow from 10.0.0.0/24 to any port 8082
sudo ufw allow from 10.0.0.0/24 to any port 8888
sudo ufw allow from 127.0.0.1 to any port 13306
```

## 📝 日常运维

### 查看日志

```bash
# 所有服务日志
docker compose logs -f

# 特定服务日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql

# 最近100行日志
docker compose logs --tail=100 backend
```

### 重启服务

```bash
# 重启所有服务
docker compose restart

# 重启特定服务
docker compose restart backend
docker compose restart frontend
```

### 更新应用

```bash
# 拉取最新代码
git pull

# 重新构建并启动
docker compose build --no-cache
docker compose up -d

# 或使用部署脚本
./deploy.sh
```

### 数据库备份

```bash
# 备份数据库
docker compose exec mysql mysqldump -uroot -p online_course > backup_$(date +%Y%m%d_%H%M%S).sql

# 恢复数据库
docker compose exec -T mysql mysql -uroot -p online_course < backup_20231209_120000.sql
```

### 查看资源使用

```bash
# 查看容器资源使用情况
docker stats

# 查看磁盘使用
docker system df

# 清理未使用的资源
docker system prune -a
```

## 🐛 故障排查

### 后端无法启动

```bash
# 查看后端日志
docker compose logs backend

# 常见问题：
# 1. 数据库未就绪 -> 等待 MySQL 启动完成
# 2. 端口被占用 -> 检查 8082 端口
# 3. 配置错误 -> 检查 application-prod.yml
```

### 前端 404 错误

```bash
# 检查前端日志
docker compose logs frontend

# 进入容器检查文件
docker compose exec frontend ls -la /usr/share/nginx/html

# 重新构建前端
docker compose build --no-cache frontend
docker compose up -d frontend
```

### 数据库连接失败

```bash
# 检查 MySQL 状态
docker compose exec mysql mysqladmin ping -uroot -p

# 检查网络连通性
docker compose exec backend ping mysql

# 查看 MySQL 日志
docker compose logs mysql
```

### VPS Nginx 502 错误

```bash
# 在 VPS 上检查
sudo nginx -t
sudo systemctl status nginx
cat /var/log/nginx/course.error.log

# 检查 VPN 连接
ping 10.0.0.2

# 检查本地服务
curl http://10.0.0.2:8888
curl http://10.0.0.2:8082/api/health
```

## 📈 监控集成

### Uptime Kuma 配置

在 Uptime Kuma (http://10.0.0.2:3001) 添加监控：

1. **前端监控**
   - Type: HTTP(s)
   - URL: https://course.example.com
   - Interval: 60s

2. **后端监控**
   - Type: HTTP(s)
   - URL: https://course.example.com/api/health
   - Interval: 60s

3. **内部监控**
   - Type: HTTP
   - URL: http://10.0.0.2:8082/api/health
   - Interval: 30s

### Netdata 监控

Netdata (http://10.0.0.2:19999) 会自动监控：

- Docker 容器资源使用
- MySQL 性能指标
- Redis 性能指标
- 系统资源（CPU、内存、磁盘、网络）

## 🎯 性能优化

### 1. JVM 调优

编辑 `docker-compose.yml` 中的后端服务：

```yaml
backend:
  environment:
    - JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### 2. MySQL 调优

编辑 `mysql/conf.d/my.cnf`：

```ini
[mysqld]
innodb_buffer_pool_size=512M  # 增加缓冲池
max_connections=500           # 增加连接数
```

### 3. Nginx 缓存

在 VPS Nginx 配置中添加缓存：

```nginx
# 在 http 块中添加
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=course_cache:10m max_size=1g inactive=60m;

# 在 location / 块中添加
proxy_cache course_cache;
proxy_cache_valid 200 10m;
```

## 📞 支持

遇到问题？检查以下资源：

1. 查看日志: `docker compose logs -f`
2. 检查健康状态: `docker compose ps`
3. 查看资源使用: `docker stats`
4. 参考本文档的故障排查部分

---

**最后更新**: 2025-12-09
**维护者**: AI Assistant
