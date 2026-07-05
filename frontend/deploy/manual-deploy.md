# 手动部署指南

如果自动部署脚本无法使用，请按照以下步骤手动部署。

## 前置要求

- 已完成本地构建：`npm run build`
- 可以 SSH 访问目标服务器

## 部署步骤

### 1. 连接到服务器

```bash
ssh root@192.0.2.20
# 密码: <YOUR_SSH_PASSWORD>
```

### 2. 创建部署目录

```bash
mkdir -p /www/wwwroot/online-course-platform
```

### 3. 上传构建文件

在本地计算机执行（新开一个终端）：

```powershell
# Windows PowerShell
scp -r .\dist\* root@192.0.2.20:/www/wwwroot/online-course-platform/
```

或者使用 FTP/SFTP 工具（如 WinSCP、FileZilla）上传 `dist` 目录下的所有文件到 `/www/wwwroot/online-course-platform/`

### 4. 配置 Nginx

在服务器上创建 Nginx 配置文件（宝塔面板）：

```bash
vi /www/server/panel/vhost/nginx/online-course-platform.conf
```

复制以下内容：

```nginx
server {
    listen 15174;
    server_name 192.0.2.20;

    root /www/wwwroot/online-course-platform;
    index index.html;

    charset utf-8;

    access_log /var/log/nginx/online-course-platform-access.log;
    error_log /var/log/nginx/online-course-platform-error.log;

    # Gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/xml text/javascript application/json application/javascript application/xml+rss application/rss+xml font/truetype font/opentype application/vnd.ms-fontobject image/svg+xml;

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://192.0.2.10:8082/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Vue Router History 模式
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 禁止访问隐藏文件
    location ~ /\. {
        deny all;
    }
}
```

### 5. 测试并重载 Nginx

```bash
# 测试配置
nginx -t

# 如果测试通过，重载 Nginx
systemctl reload nginx

# 或者重启 Nginx
systemctl restart nginx
```

### 6. 检查防火墙

确保端口 15174 已开放：

```bash
# CentOS/RHEL
firewall-cmd --zone=public --add-port=15174/tcp --permanent
firewall-cmd --reload

# Ubuntu/Debian
ufw allow 15174/tcp
ufw reload

# 或者检查云服务器的安全组规则
```

### 7. 验证部署

在浏览器访问：`http://192.0.2.20:15174`

### 8. 查看日志（如有问题）

```bash
# 查看访问日志
tail -f /var/log/nginx/online-course-platform-access.log

# 查看错误日志
tail -f /var/log/nginx/online-course-platform-error.log

# 查看 Nginx 状态
systemctl status nginx
```

## 常见问题

### Nginx 未安装

```bash
# CentOS/RHEL
yum install nginx -y

# Ubuntu/Debian
apt update && apt install nginx -y

# 启动 Nginx
systemctl start nginx
systemctl enable nginx
```

### 文件权限问题

```bash
# 设置正确的权限
chown -R nginx:nginx /www/wwwroot/online-course-platform
chmod -R 755 /www/wwwroot/online-course-platform
```

### 端口被占用

```bash
# 检查端口占用
netstat -tulnp | grep 15174

# 或使用其他端口，修改 nginx.conf 中的 listen 15174;
```

## 更新部署

再次部署时，只需要：

1. 本地重新构建：`npm run build`
2. 清空服务器目录：`rm -rf /www/wwwroot/online-course-platform/*`
3. 上传新文件：`scp -r .\dist\* root@192.0.2.20:/www/wwwroot/online-course-platform/`
4. 清除浏览器缓存，刷新页面
