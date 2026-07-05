# 快速开始指南

## 🎯 一分钟部署

### Windows 用户

1. **确保 Docker Desktop 正在运行**

2. **双击运行部署脚本**
   ```
   deploy.bat
   ```

3. **等待部署完成**（约5-10分钟）

4. **访问应用**
   - 本地访问: http://localhost:8888
   - 通过域名: https://course.example.com

### Linux/Mac 用户

```bash
# 1. 赋予执行权限
chmod +x deploy.sh

# 2. 运行部署
./deploy.sh

# 3. 等待完成，访问应用
```

---

## 📋 部署前检查清单

- [ ] Docker 已安装并运行
- [ ] Docker Compose 已安装
- [ ] WireGuard VPN 已连接（本机 IP: 10.0.0.2）
- [ ] 端口 8082, 8888, 13306, 6379 未被占用
- [ ] 已复制 `.env.example` 为 `.env` 并配置密码

---

## 🔧 首次配置

### 1. 环境变量配置

```bash
# 复制示例文件
cp .env.example .env

# 编辑配置（Windows用记事本，Linux用nano/vim）
notepad .env   # Windows
nano .env      # Linux/Mac
```

**必须修改的项**:
```env
MYSQL_ROOT_PASSWORD=你的MySQL密码
MYSQL_PASSWORD=应用数据库密码
JWT_SECRET=至少32位的随机字符串
```

### 2. 创建 Docker 网络

```bash
docker network create local_net
```

### 3. 部署

运行对应的部署脚本即可。

---

## 🌐 VPS Nginx 配置（首次部署需要）

### 在VPS上执行以下命令：

```bash
# 1. 上传配置文件（在本地执行）
scp deploy/vps-nginx-course.conf root@192.0.2.10:/tmp/

# 2. SSH登录VPS
ssh root@192.0.2.10

# 3. 移动配置文件
sudo mv /tmp/vps-nginx-course.conf /etc/nginx/sites-available/course.example.com.conf

# 4. 创建软链接
sudo ln -s /etc/nginx/sites-available/course.example.com.conf /etc/nginx/sites-enabled/

# 5. 申请SSL证书
sudo certbot certonly --nginx -d course.example.com

# 6. 测试配置
sudo nginx -t

# 7. 重载Nginx
sudo systemctl reload nginx
```

---

## ✅ 验证部署

### 1. 检查容器状态

```bash
docker compose ps
```

所有服务状态应为 `Up (healthy)`

### 2. 测试服务

```bash
# 测试前端
curl http://localhost:8888

# 测试后端健康检查
curl http://localhost:8082/api/health

# 应该返回: {"code":200,"message":"操作成功","data":{"status":"UP",...}}
```

### 3. 浏览器访问

- 本地: http://localhost:8888
- 公网: https://course.example.com

项目不提供默认管理员账号；请在部署后通过受控流程创建。

---

## 📝 常用命令

```bash
# 查看日志
docker compose logs -f

# 重启服务
docker compose restart

# 停止服务
docker compose down

# 完全清理（包括数据）
docker compose down -v

# 更新应用
git pull
./deploy.sh
```

---

## 🆘 遇到问题？

1. **容器启动失败**
   ```bash
   docker compose logs [service_name]
   ```

2. **端口被占用**
   ```bash
   # Windows
   netstat -ano | findstr "8082"
   
   # Linux/Mac
   lsof -i :8082
   ```

3. **数据库连接失败**
   - 检查 `.env` 中的数据库密码
   - 等待 MySQL 容器完全启动（约30秒）

4. **前端404错误**
   - 重新构建前端: `docker compose build --no-cache frontend`

5. **502 网关错误（VPS）**
   - 检查VPN连接: `ping 10.0.0.2`
   - 检查本地服务: `curl http://10.0.0.2:8888`

---

## 📚 详细文档

- [完整部署文档](DOCKER_DEPLOY.md)
- [监控配置指南](deploy/UPTIME_KUMA_SETUP.md)
- [API文档](http://localhost:8082/api/doc.html)

---

祝您部署顺利！🎉
