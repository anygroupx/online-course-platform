# 宝塔面板部署指南（推荐方式）

> 更新时间：2026-07-12
> API 反代目标请使用后端容器端口 **8082**（`/api`）。开发联调为 8080。


## 🎯 适用场景

服务器已安装宝塔面板，通过面板可视化操作部署。

---

## 📋 部署步骤

### 方式 1：通过宝塔面板（最简单，推荐）

#### 1. 本地构建项目

```powershell
cd j:\29\online-course-platform\frontend
npm run build
```

#### 2. 登录宝塔面板

访问：`http://192.0.2.20:8888` （或您的宝塔面板端口）

#### 3. 创建网站

- 点击 **网站** → **添加站点**
- 域名填写：`192.0.2.20` 或您的域名
- 端口：`7892`
- 根目录：`/www/wwwroot/online-course-platform`
- PHP 版本：纯静态（不选择）
- 点击提交

#### 4. 上传文件

方法 A - 使用宝塔面板文件管理器：

- 点击 **文件** → 进入 `/www/wwwroot/online-course-platform`
- 删除目录下所有文件
- 上传本地 `dist` 目录下的所有文件

方法 B - 使用 SFTP/SCP：

```powershell
scp -r .\dist\* root@192.0.2.20:/www/wwwroot/online-course-platform/
```

#### 5. 配置网站设置

点击网站后的 **设置** 按钮：

**A. 配置反向代理（API 代理）**

- 进入 **反向代理** 标签
- 点击 **添加反向代理**
  - 代理名称：`backend-api`
  - 目标 URL：`http://192.0.2.10:8082`
  - 发送域名：`$host`
  - 代理目录：`/api`
  - 内容替换：不填
- 点击保存

**B. 配置伪静态（Vue Router 支持）**

- 进入 **伪静态** 标签
- 选择 **vue** 模板，或手动填写：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

- 点击保存

**C. 开启 Gzip 压缩**

- 进入 **配置文件** 标签
- 在 `server` 块中确认有以下配置（宝塔默认已开启）：

```nginx
gzip on;
gzip_min_length  1k;
gzip_buffers     4 16k;
gzip_http_version 1.1;
gzip_comp_level 2;
gzip_types     text/plain application/javascript application/x-javascript text/javascript text/css application/xml;
gzip_vary on;
```

#### 6. 开放端口

- 点击 **安全** → 添加放行端口
- 端口：`7892`
- 协议：`TCP`
- 说明：`前端服务`
- 点击放行

#### 7. 检查云服务器安全组

如果使用阿里云/腾讯云等，需在云控制台的安全组规则中添加 7892 端口。

#### 8. 访问测试

浏览器访问：`http://192.0.2.20:7892`

---

### 方式 2：通过命令行 + Nginx 配置文件

如果您更熟悉命令行操作，可以使用提供的脚本：

#### Windows:

```powershell
npm run build
.\deploy\deploy.ps1
```

#### Linux:

```bash
npm run build
chmod +x ./deploy/deploy.sh
./deploy/deploy.sh
```

---

## 🔧 宝塔面板的配置文件路径

- **网站配置**: `/www/server/panel/vhost/nginx/online-course-platform.conf`
- **网站目录**: `/www/wwwroot/online-course-platform`
- **Nginx 主配置**: `/www/server/nginx/conf/nginx.conf`
- **日志目录**: `/www/wwwlogs/`

---

## 📝 常用操作

### 更新部署

1. 本地重新构建：`npm run build`
2. 宝塔面板 → 文件 → 删除旧文件 → 上传新文件
3. 或使用 SCP：`scp -r .\dist\* root@192.0.2.20:/www/wwwroot/online-course-platform/`
4. 清除浏览器缓存

### 查看日志

宝塔面板 → 网站 → 设置 → 日志 → 查看网站日志

### 重启服务

宝塔面板 → 软件商店 → Nginx → 重启

---

## ⚠️ 注意事项

1. **确保端口不冲突**：7892 端口未被其他应用占用
2. **文件权限**：宝塔会自动设置，通常为 `www:www`
3. **PHP 版本**：纯静态项目不需要选择 PHP 版本
4. **域名配置**：如果有域名，在添加站点时填写域名而非 IP

---

## 🎉 部署完成

访问地址：**http://192.0.2.20:7892**

如果页面无法访问，请检查：

1. 服务器防火墙（宝塔安全面板）
2. 云服务器安全组规则
3. Nginx 是否正常运行
4. 配置文件是否正确
