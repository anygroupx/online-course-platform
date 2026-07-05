# 常见问题解决方案

## 后端启动问题

### 问题1: Invalid value type for attribute 'factoryBeanObjectType'

**错误信息**:
```
java.lang.IllegalArgumentException: Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

**原因**: MyBatis Plus版本与Spring Boot 3.x不兼容

**解决方案**: 
已修复！升级MyBatis Plus到3.5.7版本

**操作步骤**:
```bash
# 1. 清理Maven缓存
cd backend
mvn clean

# 2. 重新下载依赖
mvn dependency:purge-local-repository

# 3. 重新编译
mvn clean install

# 4. 启动
mvn spring-boot:run
```

---

### 问题2: 数据库连接失败

**错误信息**:
```
Communications link failure
```

**原因**: 数据库未启动或配置错误

**解决方案**:
1. 检查MySQL是否启动
2. 检查 `application.yml` 中的数据库配置
3. 确认数据库已创建并导入数据

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/online_course?...
    username: root
    password: your_password  # 修改为实际密码
```

---

### 问题3: 端口被占用

**错误信息**:
```
Port 8080 was already in use
```

**解决方案**:
修改 `application.yml` 中的端口：
```yaml
server:
  port: 8081  # 改为其他端口
```

---

### 问题4: Redis连接失败（可选）

**错误信息**:
```
Unable to connect to Redis
```

**解决方案**:
Redis是可选功能，如果不需要可以：

1. 注释掉 `pom.xml` 中的Redis依赖
2. 注释掉 `application.yml` 中的Redis配置（已默认注释）

---

## 前端启动问题

### 问题1: 依赖安装失败

**错误信息**:
```
npm ERR! network timeout
```

**解决方案**:
使用国内镜像源：
```bash
npm config set registry https://registry.npmmirror.com
npm install
```

---

### 问题2: Vite启动失败

**错误信息**:
```
Port 5173 is already in use
```

**解决方案**:
修改 `vite.config.js` 中的端口：
```js
export default defineConfig({
  server: {
    port: 5174,  // 改为其他端口
    ...
  }
})
```

---

### 问题3: API请求404

**错误信息**:
```
404 Not Found
```

**原因**: 后端未启动或代理配置错误

**解决方案**:
1. 确保后端已启动（http://localhost:8080）
2. 检查 `vite.config.js` 中的代理配置
3. 检查后端接口路径是否正确

---

## 数据库问题

### 问题1: 导入SQL失败

**错误信息**:
```
ERROR 1064: You have an error in your SQL syntax
```

**解决方案**:
1. 确保使用UTF-8编码
2. 确保MySQL版本≥5.7
3. 使用以下命令导入：

```bash
mysql -u root -p --default-character-set=utf8mb4 online_course < schema.sql
```

---

### 问题2: 表不存在

**解决方案**:
重新导入数据库：
```bash
# 1. 删除数据库
DROP DATABASE IF EXISTS online_course;

# 2. 重新创建
CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4;

# 3. 导入
mysql -u root -p online_course < database/schema.sql
```

---

## Maven问题

### 问题1: 依赖下载慢

**解决方案**:
配置国内Maven镜像，编辑 `~/.m2/settings.xml`:

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <name>Aliyun Maven</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

---

### 问题2: 编译失败

**错误信息**:
```
Failed to execute goal
```

**解决方案**:
```bash
# 清理并重新编译
mvn clean install -DskipTests

# 如果还是失败，删除本地仓库重新下载
rm -rf ~/.m2/repository/com/baomidou
mvn clean install
```

---

## 运行时问题

### 问题1: 登录失败

**原因**: 数据库中没有用户数据

**解决方案**:
检查数据库中是否有admin用户：
```sql
SELECT * FROM sys_user WHERE username = 'admin';
```

如果没有管理员账号，请通过受控运维流程创建。不要复制固定密码哈希或在文档中保存可直接登录的凭据。

---

### 问题2: Token过期

**解决方案**:
修改 `application.yml` 增加Token有效期：
```yaml
jwt:
  expiration: 2592000  # 30天（单位：秒）
```

---

## 性能问题

### 问题1: 启动慢

**解决方案**:
1. 增加JVM内存：
```bash
export MAVEN_OPTS="-Xmx1024m"
mvn spring-boot:run
```

2. 跳过测试：
```bash
mvn spring-boot:run -DskipTests
```

---

### 问题2: 查询慢

**解决方案**:
1. 添加数据库索引
2. 启用Redis缓存
3. 优化SQL查询

---

## 其他问题

### 查看详细错误

**后端日志**:
```bash
# 查看日志文件
tail -f logs/online-course-platform.log

# 或查看控制台输出
```

**前端调试**:
```bash
# 打开浏览器控制台（F12）
# 查看Network和Console标签
```

---

## 快速重置

### 完全重置项目

```bash
# 1. 清理后端
cd backend
mvn clean

# 2. 清理前端
cd frontend
rm -rf node_modules
rm package-lock.json

# 3. 重置数据库
mysql -u root -p
DROP DATABASE online_course;
CREATE DATABASE online_course;
USE online_course;
SOURCE database/schema.sql;

# 4. 重新启动
# 后端：mvn spring-boot:run
# 前端：npm install && npm run dev
```

---

## 联系支持

如果以上方案无法解决问题，请：

1. 查看完整错误日志
2. 检查环境配置（JDK、Maven、Node.js版本）
3. 查看在线文档：http://localhost:8080/api/doc.html
4. 查看项目README.md和其他文档

---

## 成功启动标志

### 后端成功
```
========================================
   在线网课平台启动成功！
   
   API地址: http://localhost:8080/api
   文档地址: http://localhost:8080/api/doc.html
========================================
```

### 前端成功
```
  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```

---

**祝您顺利启动！** 🚀
