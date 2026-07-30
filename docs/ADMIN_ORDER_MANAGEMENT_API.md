# 管理员订单管理API文档

> 更新时间：2026-07-12
> Base：开发 `http://localhost:8080/api`，Docker `http://localhost:8082/api`
> 权限：管理员（角色 ADMIN / 历史逻辑可能校验特定用户）
> 完整 API 索引见 [API_DOCUMENTATION.md](./API_DOCUMENTATION.md)


## 概述
管理员订单管理功能为系统管理员提供了对自营平台订单的完整管理能力，包括订单查询、状态管理、批量操作、统计报表等功能。

## 权限说明
- 所有接口都需要管理员权限（用户ID = 1）
- 非管理员用户访问将返回403 Forbidden错误

## API接口列表

### 1. 查询所有订单
**接口地址：** `POST /api/admin/orders/query-all`

**功能描述：** 管理员查询平台所有订单，无权限限制

**请求参数：**
```json
{
  "orderNo": "订单编号（可选）",
  "platformId": "平台ID（可选）",
  "studentAccount": "学生账号（可选）",
  "orderStatus": "订单状态（可选）",
  "dockStatus": "对接状态（可选）",
  "page": 1,
  "pageSize": 10
}
```

**响应示例：**
```json
{
  "code": 1,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 1,
        "orderNo": "ORD20250117001",
        "platformName": "智慧树",
        "studentAccount": "student001",
        "courseName": "高等数学",
        "amount": 15.00,
        "progress": "85%",
        "orderStatus": 2,
        "dockStatus": 1,
        "retryCount": 0,
        "createTime": "2025-01-17 10:00:00"
      }
    ],
    "total": 100,
    "current": 1,
    "size": 10
  }
}
```

### 2. 获取订单统计信息
**接口地址：** `GET /api/admin/orders/statistics`

**功能描述：** 获取平台订单统计信息

**响应示例：**
```json
{
  "code": 1,
  "message": "查询成功",
  "data": {
    "totalOrders": 1000,
    "pendingOrders": 50,
    "processingOrders": 30,
    "completedOrders": 800,
    "cancelledOrders": 80,
    "failedOrders": 40,
    "totalRevenue": 15000.00,
    "todayOrders": 25,
    "todayRevenue": 375.00
  }
}
```

### 3. 强制修改订单状态
**接口地址：** `POST /api/admin/orders/{orderId}/force-update-status`

**功能描述：** 管理员强制修改订单状态

**请求参数：**
- `orderId`: 订单ID（路径参数）
- `newStatus`: 新状态（0-待处理，1-进行中，2-已完成，3-已取消，4-失败）
- `reason`: 修改原因（可选）

**响应示例：**
```json
{
  "code": 1,
  "message": "订单状态修改成功"
}
```

### 4. 强制修改对接状态
**接口地址：** `POST /api/admin/orders/{orderId}/force-update-dock-status`

**功能描述：** 管理员强制修改对接状态

**请求参数：**
- `orderId`: 订单ID（路径参数）
- `newStatus`: 新状态（0-待对接，1-对接成功，2-对接失败，3-重复订单，4-已取消）
- `reason`: 修改原因（可选）

### 5. 添加订单备注
**接口地址：** `POST /api/admin/orders/{orderId}/add-remark`

**功能描述：** 管理员为订单添加备注

**请求参数：**
- `orderId`: 订单ID（路径参数）
- `remark`: 备注内容

### 6. 查看订单详情
**接口地址：** `GET /api/admin/orders/{orderId}/detail`

**功能描述：** 管理员查看任意订单详情

**响应示例：**
```json
{
  "code": 1,
  "message": "查询成功",
  "data": {
    "id": 1,
    "orderNo": "ORD20250117001",
    "platformName": "智慧树",
    "schoolName": "北京大学",
    "studentName": "张三",
    "studentAccount": "student001",
    "courseName": "高等数学",
    "amount": 15.00,
    "progress": "85%",
    "orderStatus": 2,
    "dockStatus": 1,
    "retryCount": 0,
    "remarks": "备注信息",
    "createTime": "2025-01-17 10:00:00",
    "updateTime": "2025-01-17 11:00:00"
  }
}
```

### 7. 批量操作订单
**接口地址：** `POST /api/admin/orders/batch-operation`

**功能描述：** 管理员批量操作订单

**请求参数：**
- `orderIds`: 订单ID列表
- `operation`: 操作类型（updateStatus/updateDockStatus/addRemark）
- `value`: 操作值
- `reason`: 操作原因（可选）

## 批量操作API

### 1. 批量修改订单状态
**接口地址：** `POST /api/admin/orders/batch/update-order-status`

**请求参数：**
- `orderIds`: 订单ID列表（JSON数组）
- `status`: 新状态
- `reason`: 修改原因（可选）

### 2. 批量修改对接状态
**接口地址：** `POST /api/admin/orders/batch/update-dock-status`

### 3. 批量添加备注
**接口地址：** `POST /api/admin/orders/batch/add-remarks`

**请求参数：**
- `orderIds`: 订单ID列表
- `remark`: 备注内容

### 4. 批量补单
**接口地址：** `POST /api/admin/orders/batch/retry-orders`

**请求参数：**
- `orderIds`: 订单ID列表
- `reason`: 补单原因（可选）

## 状态码说明

### 订单状态
- 0: 待处理
- 1: 进行中
- 2: 已完成
- 3: 已取消
- 4: 失败

### 对接状态
- 0: 待对接
- 1: 对接成功
- 2: 对接失败
- 3: 重复订单
- 4: 已取消

## 错误码说明
- 403: 权限不足（非管理员用户）
- 404: 订单不存在
- 500: 服务器内部错误

## 使用示例

### JavaScript调用示例
```javascript
// 查询所有订单
const queryOrders = async () => {
  const response = await fetch('/api/admin/orders/query-all', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      orderStatus: 0,
      page: 1,
      pageSize: 10
    })
  });
  const data = await response.json();
  return data;
};

// 修改订单状态
const updateOrderStatus = async (orderId, newStatus, reason) => {
  const response = await fetch(`/api/admin/orders/${orderId}/force-update-status`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Authorization': `Bearer ${token}`
    },
    body: `newStatus=${newStatus}&reason=${encodeURIComponent(reason)}`
  });
  const data = await response.json();
  return data;
};
```

## 注意事项
1. 所有管理员操作都会记录到操作日志中
2. 批量操作支持事务回滚，确保数据一致性
3. 补单操作有次数限制（最多5次）
4. 强制修改状态会覆盖系统自动状态，请谨慎使用
5. 所有时间格式为：yyyy-MM-dd HH:mm:ss
