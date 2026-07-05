# 部署指南

## 开发环境部署

### 环境要求

- **JDK**: 17 或更高版本
- **Maven**: 3.8+ 
- **Node.js**: 18+ 
- **MySQL**: 8.0+
- **操作系统**: Windows / Linux / macOS

---

### 1. 数据库部署

#### 1.1 创建数据库

```sql
CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4;
```

#### 1.2 导入数据库结构

```bash
# Windows
cd database
mysql -u root -p online_course < schema.sql

# Linux/macOS
cd database
mysql -u root -p online_course < schema.sql
```

#### 1.3 验证数据

```sql
USE online_course;
SHOW TABLES;
SELECT * FROM sys_user WHERE id = 1;
```

---

### 2. 后端部署

#### 2.1 修改配置文件

编辑 `backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/online_course?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password  # 修改为你的数据库密码
```

#### 2.2 方式一：使用启动脚本（推荐）

**Windows**:
```bash
start-backend.bat
```

**Linux/macOS**:
```bash
chmod +x start-backend.sh
./start-backend.sh
```

#### 2.3 方式二：手动启动

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

#### 2.4 验证启动成功

访问以下地址：

- **API文档**: http://localhost:8080/api/doc.html
- **健康检查**: http://localhost:8080/api/auth/login

---

### 3. 前端部署

#### 3.1 安装依赖

```bash
cd frontend
npm install
```

如果速度慢，可以使用淘宝镜像：

```bash
npm config set registry https://registry.npmmirror.com
npm install
```

#### 3.2 方式一：使用启动脚本（推荐）

**Windows**:
```bash
start-frontend.bat
```

**Linux/macOS**:
```bash
chmod +x start-frontend.sh
./start-frontend.sh
```

#### 3.3 方式二：手动启动

```bash
cd frontend
npm run dev
```

#### 3.4 验证启动成功

访问：http://localhost:5173

默认账号：
- 项目不提供默认管理员账号；请在部署后通过受控流程创建

---

## 生产环境部署

### 1. 后端打包

```bash
cd backend
mvn clean package -DskipTests
```

生成的jar包位置：`backend/target/online-course-platform-1.0.0.jar`

### 2. 后端运行

```bash
java -jar online-course-platform-1.0.0.jar

# 或者指定配置文件
java -jar online-course-platform-1.0.0.jar --spring.config.location=application-prod.yml
```

### 3. 前端打包

```bash
cd frontend
npm run build
```

生成的静态文件位置：`frontend/dist/`

### 4. 前端部署（Nginx）

**nginx.conf配置示例**:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /var/www/online-course-platform/frontend/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端API代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

## Docker部署（可选）

### 1. 后端Dockerfile

创建 `backend/Dockerfile`:

```dockerfile
FROM openjdk:17-jdk-alpine
VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

### 2. 前端Dockerfile

创建 `frontend/Dockerfile`:

```dockerfile
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 3. Docker Compose

创建 `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: online_course
    ports:
      - "3306:3306"
    volumes:
      - ./database:/docker-entrypoint-initdb.d

  backend:
    build: ./backend
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/online_course
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root123

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - backend
```

启动：
```bash
docker-compose up -d
```

---

## 常见问题

### 1. 后端启动失败

**问题**: 端口被占用
**解决**: 修改 `application.yml` 中的端口号

**问题**: 数据库连接失败
**解决**: 检查数据库配置和数据库服务是否启动

### 2. 前端访问404

**问题**: API请求404
**解决**: 确保后端已启动，检查 `vite.config.js` 中的代理配置

### 3. Token过期

**问题**: 频繁登出
**解决**: 修改 `application.yml` 中的 `jwt.expiration` 配置

---

## 性能优化建议

### 1. 数据库优化

```sql
-- 创建索引
ALTER TABLE sys_user ADD INDEX idx_username (username);
ALTER TABLE course_order ADD INDEX idx_student_account (student_account);
ALTER TABLE course_order ADD INDEX idx_order_status (order_status);
```

### 2. 应用优化

- 启用Redis缓存
- 配置连接池参数
- 调整JVM参数

```bash
java -Xms512m -Xmx1024m -jar app.jar
```

### 3. Nginx优化

```nginx
# 开启gzip压缩
gzip on;
gzip_types text/plain text/css application/json application/javascript;

# 开启缓存
location ~* \.(js|css|png|jpg|jpeg|gif|ico)$ {
    expires 30d;
}
```

---

## 监控和日志

### 1. 应用日志

日志文件位置：`logs/online-course-platform.log`

### 2. 查看实时日志

```bash
tail -f logs/online-course-platform.log
```

---

## 备份和恢复

### 1. 数据库备份

```bash
mysqldump -u root -p online_course > backup_$(date +%Y%m%d).sql
```

### 2. 数据库恢复

```bash
mysql -u root -p online_course < backup_20250117.sql
```

---

## 安全建议

1. **修改默认密码**: 登录后立即修改admin密码
2. **修改JWT密钥**: 修改 `application.yml` 中的 `jwt.secret`
3. **启用HTTPS**: 生产环境使用SSL证书
4. **防火墙配置**: 只开放必要端口
5. **定期备份**: 设置自动备份任务

---

## 技术支持

如有问题，请查看：
- API文档：http://localhost:8080/api/doc.html
- 项目README: README.md
- 功能对比: FEATURE_COMPARISON.md
