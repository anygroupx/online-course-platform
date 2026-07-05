# API 接口文档

> 在线网课平台 RESTful API 接口说明

## 基础信息

- **Base URL**: `http://localhost:8080/api`
- **认证方式**: JWT Bearer Token
- **请求格式**: JSON
- **响应格式**: JSON

---

## 统一响应格式

```json
{
  "code": 1,
  "message": "操作成功",
  "data": {},
  "timestamp": 1705478400000
}
```

### 响应码说明

| Code | 说明 |
|------|------|
| 1 | 成功 |
| -1 | 失败 |
| -2 | 参数错误 |
| -100 | 未登录或Token过期 |
| -101 | 无权限 |
| -200 | 余额不足 |
| -206 | 订单已存在 |

---

## 1. 认证接口

### 1.1 用户登录

**接口**: `POST /auth/login`

**请求参数**:
```json
{
  "username": "admin",
  "password": "<YOUR_PASSWORD>"
}
```

**响应数据**:
```json
{
  "token": "<REDACTED_JWT>",
  "userId": 1,
  "username": "admin",
  "nickname": "管理员",
  "balance": 10000.00,
  "rate": 0.20,
  "isAdmin": true
}
```

### 1.2 用户登出

**接口**: `POST /auth/logout`

**请求头**: `Authorization: Bearer {token}`

---

## 2. 用户管理接口

### 2.1 获取用户完整信息

**接口**: `GET /user/info`

**请求头**: `Authorization: Bearer {token}`

**响应数据**:
```json
{
  "userId": 1,
  "username": "admin",
  "nickname": "管理员",
  "balance": 10000.00,
  "rate": 0.20,
  "apiKey": "abc123",
  "inviteCode": "123456",
  "totalOrders": 100,
  "totalRecharge": 5000.00,
  "agentStats": {
    "totalAgents": 50,
    "todayRegistered": 5,
    "todayLogin": 20,
    "todayOrders": 30
  }
}
```

### 2.2 查询用户列表

**接口**: `GET /users`

**请求参数**:
- `keyword`: 搜索关键词（可选）
- `status`: 状态（可选）
- `page`: 页码（默认1）
- `pageSize`: 每页数量（默认10）

### 2.3 创建用户（开户）

**接口**: `POST /users`

**请求参数**:
```json
{
  "username": "user001",
  "password": "<YOUR_PASSWORD>",
  "nickname": "新用户",
  "rate": 1.00
}
```

### 2.4 充值

**接口**: `POST /users/recharge`

**请求参数**:
```json
{
  "targetUserId": 2,
  "amount": 100.00
}
```

### 2.5 修改密码

**接口**: `POST /users/change-password`

**请求参数**:
- `oldPassword`: 旧密码
- `newPassword`: 新密码

### 2.6 重置密码

**接口**: `POST /users/{id}/reset-password`

**说明**: 重置指定用户密码，返回新密码

---

## 3. 订单管理接口

### 3.1 创建订单

**接口**: `POST /orders`

**请求参数**:
```json
{
  "platformId": 1,
  "schoolName": "某某大学",
  "studentName": "张三",
  "studentAccount": "student001",
  "studentPassword": "password123",
  "courseId": "course_001",
  "courseName": "大学英语",
  "isFastMode": false
}
```

### 3.2 查询订单列表

**接口**: `POST /orders/query`

**请求参数**:
```json
{
  "orderNo": "",
  "platformId": null,
  "studentAccount": "",
  "orderStatus": null,
  "dockStatus": null,
  "page": 1,
  "pageSize": 10
}
```

### 3.3 获取订单详情

**接口**: `GET /orders/{id}`

### 3.4 取消订单

**接口**: `POST /orders/{id}/cancel`

### 3.5 补单

**接口**: `POST /orders/{id}/retry`

### 3.6 刷新订单进度

**接口**: `POST /orders/{id}/refresh`

### 3.7 批量下单

**接口**: `POST /orders/batch`

**请求参数**:
```json
{
  "platformId": 1,
  "orders": [
    {
      "schoolName": "某某大学",
      "studentAccount": "student001",
      "studentPassword": "pass001",
      "courseId": "course_001",
      "courseName": "大学英语"
    },
    {
      "schoolName": "某某大学",
      "studentAccount": "student002",
      "studentPassword": "pass002",
      "courseId": "course_002",
      "courseName": "高等数学"
    }
  ]
}
```

---

## 4. 查课功能

### 4.1 查询课程列表

**接口**: `POST /courses/query`

**请求参数**:
```json
{
  "platformId": 1,
  "schoolName": "某某大学",
  "studentAccount": "student001",
  "studentPassword": "password123"
}
```

**响应数据**:
```json
{
  "studentName": "张三",
  "studentAccount": "student001",
  "schoolName": "某某大学",
  "courses": [
    {
      "id": "course_001",
      "name": "大学英语1",
      "description": "基础英语课程",
      "endTime": "2025-12-31",
      "selected": false
    }
  ],
  "message": "查询成功"
}
```

---

## 5. 课程平台接口

### 5.1 获取课程平台列表

**接口**: `GET /courses`

**说明**: 获取所有可用的课程平台

### 5.2 获取课程平台详情

**接口**: `GET /courses/{id}`

---

## 6. 统计数据接口

### 6.1 获取统计数据

**接口**: `GET /statistics`

**响应数据**:
```json
{
  "totalOrders": 1000,
  "todayOrders": 50,
  "totalUsers": 200,
  "todayNewUsers": 5,
  "totalAmount": 50000.00,
  "todayAmount": 2000.00,
  "pendingOrders": 10,
  "processingOrders": 20
}
```

---

## 7. 操作日志接口

### 7.1 查询操作日志

**接口**: `GET /logs`

**请求参数**:
- `operationType`: 操作类型（可选）
- `page`: 页码（默认1）
- `pageSize`: 每页数量（默认20）

---

## 8. 外部API接口（供第三方调用）

### 8.1 查询余额

**接口**: `POST /api/external/getmoney`

**请求参数**:
- `uid`: 用户ID
- `key`: API密钥

**响应数据**:
```json
{
  "money": 1000.00
}
```

### 8.2 查单

**接口**: `POST /api/external/chadan`

**请求参数**:
- `username`: 学生账号

### 8.3 单下单

**接口**: `POST /api/external/add`

**请求参数**:
- `uid`: 用户ID
- `key`: API密钥
- `platform`: 平台ID
- `school`: 学校名称
- `user`: 学生账号
- `pass`: 学生密码
- `kcid`: 课程ID
- `kcname`: 课程名称

### 8.4 补单

**接口**: `POST /api/external/budan`

**请求参数**:
- `id`: 订单ID

---

## 9. 注册和邀请码接口

### 9.1 用户注册

**接口**: `POST /register`

**请求参数**:
```json
{
  "username": "newuser",
  "password": "<YOUR_PASSWORD>",
  "inviteCode": "123456",
  "nickname": "新用户"
}
```

### 9.2 设置邀请码

**接口**: `POST /register/invite-code`

**请求参数**:
```json
{
  "inviteRate": 0.60,
  "customInviteCode": "888888"
}
```

### 9.3 验证邀请码

**接口**: `GET /register/validate-invite-code`

**请求参数**:
- `inviteCode`: 邀请码

---

## 10. API密钥管理

### 10.1 开通API密钥

**接口**: `POST /api-keys/enable`

**请求参数**:
- `type`: 开通类型（1-自己 2-下级）
- `targetUserId`: 目标用户ID（type=2时必填）

**说明**:
- 余额≥300元：免费开通
- 余额<300元：收费10元
- 给下级开通：收费5元

---

## 11. 管理员接口

### 11.1 课程平台管理

- `GET /admin/platforms` - 查询课程平台列表
- `POST /admin/platforms` - 创建课程平台
- `PUT /admin/platforms` - 更新课程平台
- `DELETE /admin/platforms/{id}` - 删除课程平台

### 11.2 API接口管理

- `GET /admin/api-providers` - 查询API接口列表
- `POST /admin/api-providers` - 创建API接口
- `PUT /admin/api-providers` - 更新API接口
- `DELETE /admin/api-providers/{id}` - 删除API接口

### 11.3 系统配置

- `GET /system/config` - 获取所有配置
- `PUT /system/config` - 更新配置

---

## 使用示例

### cURL示例

**登录**:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<YOUR_ADMIN_USERNAME>","password":"<YOUR_PASSWORD>"}'
```

**创建订单**:
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "platformId": 1,
    "studentAccount": "student001",
    "studentPassword": "pass123",
    "courseName": "大学英语"
  }'
```

### JavaScript示例

```javascript
// 登录
const loginResponse = await axios.post('/api/auth/login', {
  username: '<YOUR_ADMIN_USERNAME>',
  password: '<YOUR_PASSWORD>'
})

const token = loginResponse.data.data.token

// 创建订单
const orderResponse = await axios.post('/api/orders', {
  platformId: 1,
  studentAccount: 'student001',
  studentPassword: 'pass123',
  courseName: '大学英语'
}, {
  headers: {
    'Authorization': `Bearer ${token}`
  }
})
```

---

## 在线API文档

启动后端服务后，访问Knife4j在线文档：

```
http://localhost:8080/api/doc.html
```

在线文档提供：
- 完整的API列表
- 请求参数说明
- 响应数据示例
- 在线接口测试
