# 本地开发快速开始

> 更新时间：2026-07-12
> Docker 方式请看仓库根目录 [QUICKSTART.md](../QUICKSTART.md)

## 环境要求

- JDK **17+**
- Maven **3.8+**
- Node.js **18+**（推荐 20）
- MySQL **8.0+**
- （可选）Redis 7、Elasticsearch 8.11

## 1. 数据库

```bash
mysql -u root -p -e "CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4;"
mysql -u root -p online_course < database/schema.sql

# 建议同步执行迁移
for f in database/migrations/*.sql; do mysql -u root -p online_course < "$f"; done

# 可选测试数据
mysql -u root -p online_course < database/test_data.sql
```

## 2. 后端

配置文件：

```
backend/course-web/src/main/resources/application.yml
```

修改 `spring.datasource.url/username/password` 为本机数据库。
如无 ES，可暂时注释或指向可用实例。

```bash
cd backend
mvn clean install -DskipTests
mvn -pl course-web -am spring-boot:run
```

| 项 | 值 |
|----|----|
| 端口 | **8080** |
| 上下文 | `/api` |
| 健康检查 | http://localhost:8080/api/health |
| API 文档 | http://localhost:8080/api/doc.html |

> 不要使用已过时的 `cd backend && mvn spring-boot:run`（根模块为 pom 聚合）。
> 正确方式：`-pl course-web -am`。

### 使用脚本

```bash
# 仓库提供的脚本位于 docs/ 下，内容可能仍是旧命令，推荐直接用上面的 Maven 命令
# 或自行：
cd backend && mvn -pl course-web -am spring-boot:run
```

## 3. 前端

```bash
cd frontend
npm install
npm run dev
```

| 项 | 值 |
|----|----|
| 地址 | http://localhost:5173 |
| 代理 | 见 `vite.config.js`（通常代理 `/api` → 后端） |

生产构建：

```bash
npm run build
npm run preview
```

## 4. 默认账号

- 用户名：`admin`
- 密码：`123456`

## 5. 验证

```bash
# 健康检查
curl http://localhost:8080/api/health

# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

## 6. 常见问题

| 现象 | 处理 |
|------|------|
| 端口占用 | 修改 `server.port` 或释放 8080/5173 |
| 数据库连不上 | 检查 URL、账号、时区参数 `serverTimezone=Asia/Shanghai` |
| ES 启动失败 | 开发可先停用相关功能或启动 ES |
| 前端 404 / 跨域 | 确认 vite 代理与后端 `allowed-origins` |
| Maven 找不到主类 | 确认使用 `-pl course-web -am` |

更多见 [TROUBLESHOOTING.md](./TROUBLESHOOTING.md)。
