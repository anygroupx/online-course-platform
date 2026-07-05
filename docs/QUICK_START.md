# 快速开始指南

## 5分钟快速启动

### 第一步：准备环境

确保已安装：
- ✅ JDK 17+
- ✅ Maven 3.8+
- ✅ Node.js 18+
- ✅ MySQL 8.0+

### 第二步：配置数据库

```sql
-- 1. 创建数据库
CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4;

-- 2. 导入数据
USE online_course;
SOURCE database/schema.sql;
```

### 第三步：启动后端

#### Windows用户（双击启动）

```
双击运行：start-backend.bat
```

#### 手动启动

```bash
cd backend

# 修改配置文件（可选）
# vim src/main/resources/application.yml

# 启动
mvn spring-boot:run
```

等待看到以下提示：

```
========================================
   在线网课平台启动成功！
   
   API地址: http://localhost:8080/api
   文档地址: http://localhost:8080/api/doc.html
========================================
```

### 第四步：启动前端

#### Windows用户（双击启动）

```
双击运行：start-frontend.bat
```

#### 手动启动

```bash
cd frontend

# 安装依赖（首次运行）
npm install

# 启动
npm run dev
```

等待看到：

```
  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### 第五步：登录使用

1. 打开浏览器访问：http://localhost:5173
2. 输入默认账号：
   - 使用部署后通过受控流程创建的管理员账号
3. 登录成功！

---

## 核心功能演示

### 1. 查看首页

登录后自动进入首页，可以看到：
- 账户余额
- 订单总数
- 我的费率
- 总充值金额
- 代理统计数据

### 2. 创建订单

点击"订单管理" → "新建订单"：
- 选择课程平台
- 填写学生信息
- 填写课程名称
- 提交订单

### 3. 管理代理

点击"代理管理" → "开户"：
- 填写账号信息
- 设置费率
- 提交开户

### 4. 充值余额

在代理列表中选择用户 → 点击"充值"：
- 输入充值金额
- 系统自动计算实际扣费
- 确认充值

### 5. 查看日志

点击"操作日志"，可以看到所有操作记录：
- 操作类型
- 操作描述
- 金额变动
- IP地址
- 操作时间

---

## 管理员功能

### 1. 管理课程平台

点击"平台管理"（仅管理员可见）：
- 查看所有课程平台
- 添加新平台
- 编辑平台信息
- 设置价格和费率

### 2. 查看API文档

访问：http://localhost:8080/api/doc.html

可以：
- 查看所有API接口
- 在线测试接口
- 查看请求/响应示例

---

## API对接示例

### 1. 获取API密钥

登录系统 → 首页 → 点击"开通API"

### 2. 调用外部API

```bash
# 查询余额
curl -X POST http://localhost:8080/api/api/external/getmoney \
  -d "uid=1&key=your_api_key"

# 创建订单
curl -X POST http://localhost:8080/api/api/external/add \
  -d "uid=1&key=your_api_key&platform=1&user=student001&pass=password&kcname=大学英语"
```

---

## 常见问题

### Q1: 后端启动失败？

**检查清单**：
1. JDK版本是否≥17
2. MySQL是否启动
3. 数据库配置是否正确
4. 端口8080是否被占用

### Q2: 前端无法访问？

**检查清单**：
1. Node.js版本是否≥18
2. 依赖是否安装完成（npm install）
3. 后端是否已启动
4. 端口5173是否被占用

### Q3: 登录失败？

**检查清单**：
1. 数据库是否导入成功
2. 管理员账号是否已安全创建，凭据是否正确
3. 后端是否正常启动

### Q4: API调用401错误？

**原因**: Token未传递或已过期

**解决**: 
1. 检查请求头是否包含 `Authorization: Bearer {token}`
2. 重新登录获取新Token

---

## 下一步

### 学习资源

- 📖 **API文档**: `API_DOCUMENTATION.md`
- 🚀 **部署指南**: `DEPLOYMENT_GUIDE.md`
- 📊 **功能清单**: `FINAL_FEATURE_LIST.md`
- 🔍 **功能对比**: `FEATURE_COMPARISON.md`

### 二次开发

查看源码注释，所有代码都有详细的中文注释说明。

### 技术支持

遇到问题请查看：
1. 项目文档
2. API在线文档
3. 控制台日志
4. 数据库日志

---

**祝您使用愉快！** 🎉
