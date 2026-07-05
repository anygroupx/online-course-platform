# CORS问题修复总结

## 问题根源

**SecurityConfig.java 第125-131行硬编码了CORS允许的源**，没有读取 `application.yml` 中的配置！

原代码：
```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:5173",
    "http://localhost:3000",
    "http://localhost:8080",
    "http://localhost:15174",
    "https://tunnel.example.com:24772"
));
```

**缺少** `http://192.168.1.10:8888`！

---

## 已修复内容

### 1. SecurityConfig.java改为从配置读取

```java
// 添加配置注入
@Value("${course.security.allowed-origins}")
private List<String> allowedOrigins;

// 使用配置的值
configuration.setAllowedOrigins(allowedOrigins);
```

### 2. 添加健康检查端点到白名单

```java
private static final String[] PERMIT_ALL_PATHS = {
    "/auth/**",
    "/health",   // ← 新增
    "/ping",     // ← 新增
    // ...
};
```

### 3. application.yml中的配置

```yaml
course:
  security:
    allowed-origins:
      - http://localhost:5173
      - http://localhost:3000
      - http://localhost:15174
      - http://localhost:8888
      - http://192.168.1.10:8888  # Docker容器
      - http://10.0.0.2:8888       # VPN
      - https://course.example.com  # 生产域名
```

---

## 重新部署步骤（在开发机上）

```bash
# 1. 重新构建后端（必须，因为改了Java代码）
docker compose build backend

# 2. 重启后端
docker compose up -d backend

# 3. 等待后端启动（约30秒）
sleep 30

# 4. 测试CORS预检请求
curl -H "Origin: http://192.168.1.10:8888" \
     -H "Access-Control-Request-Method: POST" \
     -H "Access-Control-Request-Headers: Content-Type,Authorization" \
     -X OPTIONS \
     http://192.168.1.10:8082/api/auth/login -v

# 应该看到响应头：
# Access-Control-Allow-Origin: http://192.168.1.10:8888
# Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
# Access-Control-Allow-Credentials: true

# 5. 测试登录API
curl -X POST http://192.168.1.10:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -H "Origin: http://192.168.1.10:8888" \
  -d '{"username":"<YOUR_ADMIN_USERNAME>","password":"<YOUR_PASSWORD>"}' -v

# 6. 查看日志
docker compose logs backend --tail=50
```

---

## 验证清单

### 后端验证
- [ ] 后端容器成功启动
- [ ] curl OPTIONS请求返回CORS头
- [ ] curl POST请求返回token
- [ ] 后端日志无CORS错误

### 前端验证
- [ ] 清除浏览器缓存（Ctrl+Shift+Delete）
- [ ] 硬刷新页面（Ctrl+F5）
- [ ] F12开发者工具 → Network标签
- [ ] 点击登录
- [ ] OPTIONS预检请求成功（204或200）
- [ ] POST登录请求成功（200）
- [ ] 登录成功跳转到主页

---

## 如果仍有问题

### 检查后端日志
```bash
docker compose logs backend --tail=100 | grep -i cors
docker compose logs backend --tail=100 | grep -i error
```

### 检查前端Network
打开F12 → Network → 查看：
1. **OPTIONS预检请求**：
   - URL: `http://192.168.1.10:8082/api/auth/login`
   - Method: `OPTIONS`
   - Status: 应该是 204 或 200
   - Response Headers 应包含:
     ```
     Access-Control-Allow-Origin: http://192.168.1.10:8888
     Access-Control-Allow-Methods: ...
     Access-Control-Allow-Credentials: true
     ```

2. **POST登录请求**：
   - URL: `http://192.168.1.10:8082/api/auth/login`
   - Method: `POST`
   - Status: 应该是 200
   - Response 应包含 token

---

最后更新: 2025-12-09
