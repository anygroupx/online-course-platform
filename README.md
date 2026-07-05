# 在线网课平台

> 基于 Spring Boot 3 + Vue 3 的前后端分离在线网课管理平台

> 说明：本仓库当前作为历史线的最终完整快照保存，`backend/` 与
> `frontend/` 已取消子模块并直接纳入仓库。后续迭代请在
> `anygroupx` 组织下继续进行。

## 📋 项目简介

这是一个现代化的在线网课管理平台，采用前后端分离架构，提供课程管理、订单管理、用户管理、代理系统等功能。

### 技术栈

**后端：**
- Spring Boot 3.2.1
- Spring Security + JWT
- MyBatis Plus 3.5.5
- MySQL 8.0+
- Redis (可选)
- Knife4j (API文档)

**前端：**
- Vue 3.4+
- Vite 5.0+
- Element Plus 2.5+
- Pinia (状态管理)
- Vue Router 4
- Axios

## 🚀 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Maven 3.8+

### 后端启动

1. **创建数据库**

```sql
CREATE DATABASE online_course DEFAULT CHARACTER SET utf8mb4;
```

2. **导入数据库**

```bash
cd database
mysql -u root -p online_course < schema.sql
```

3. **修改配置文件**

编辑 `backend/src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/online_course?...
    username: root
    password: your_password
```

4. **启动后端服务**

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动

API文档地址：`http://localhost:8080/api/doc.html`

### 前端启动

1. **安装依赖**

```bash
cd frontend
npm install
```

2. **启动开发服务器**

```bash
npm run dev
```

前端服务将在 `http://localhost:5173` 启动

3. **构建生产版本**

```bash
npm run build
```

## 📁 项目结构

```
online-course-platform/
├── database/                # 数据库脚本
│   └── schema.sql          # 数据库结构
├── backend/                 # 后端项目
│   ├── src/main/java/
│   │   └── com/course/platform/
│   │       ├── common/              # 公共模块
│   │       │   ├── constant/       # 常量定义
│   │       │   ├── exception/      # 异常处理
│   │       │   ├── result/         # 统一响应
│   │       │   └── util/           # 工具类
│   │       ├── config/             # 配置类
│   │       ├── domain/             # 领域模型
│   │       │   ├── entity/        # 实体类
│   │       │   ├── dto/           # 数据传输对象
│   │       │   └── vo/            # 视图对象
│   │       ├── mapper/             # 数据访问层
│   │       ├── service/            # 业务逻辑层
│   │       ├── controller/         # 控制器层
│   │       └── security/           # 安全配置
│   └── src/main/resources/
│       └── application.yml         # 配置文件
└── frontend/                # 前端项目
    ├── src/
    │   ├── api/            # API接口
    │   ├── assets/         # 静态资源
    │   ├── components/     # 公共组件
    │   ├── layouts/        # 布局组件
    │   ├── router/         # 路由配置
    │   ├── stores/         # 状态管理
    │   ├── utils/          # 工具函数
    │   ├── views/          # 页面组件
    │   ├── App.vue         # 根组件
    │   └── main.js         # 入口文件
    ├── index.html
    ├── vite.config.js      # Vite配置
    └── package.json
```

## 📊 核心功能模块（已完成95%+）

### ✅ 用户系统
- 用户登录/登出（JWT认证）
- 用户注册（邀请码）
- 用户CRUD管理
- 多级代理系统
- 费率管理
- 余额充值
- 密码修改/重置
- 邀请码系统

### ✅ 订单系统
- 订单创建
- 批量下单
- 订单查询（分页、多条件）
- 订单详情
- 取消订单
- 补单功能（限制5次）
- 订单进度同步
- 重复订单检测

### ✅ 查课功能
- 单个查课
- 批量查课
- 课程列表返回
- 第三方API对接

### ✅ 课程管理
- 课程平台列表
- 课程平台CRUD（管理员）
- 价格自动计算
- 费率配置

### ✅ API密钥
- 开通API密钥
- 密钥验证
- 外部API接口

### ✅ 日志系统
- 操作日志自动记录
- 日志查询（分页）
- 金额变动追踪

### ✅ 统计分析
- 订单统计
- 用户统计
- 代理统计
- 数据报表

### ✅ 系统配置
- 参数配置管理
- 第三方接口管理
- 系统公告

## 🌟 项目特色

### 现代化UI设计
- ✅ 渐变主题色彩
- ✅ 毛玻璃效果
- ✅ 流畅动画过渡
- ✅ 响应式布局
- ✅ 微交互反馈

### 完整功能
- ✅ 订单导出（4种格式）
- ✅ 价格列表对比
- ✅ 批量操作
- ✅ 智能文本解析
- ✅ 数据统计分析

## 🚀 一键启动

### Windows用户
```bash
# 启动后端
start-backend.bat

# 启动前端（新开一个命令行窗口）
start-frontend.bat
```

### Linux/macOS用户
```bash
# 启动后端
chmod +x start-backend.sh && ./start-backend.sh

# 启动前端
chmod +x start-frontend.sh && ./start-frontend.sh
```

### 手动启动
```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```

## 🔐 管理员账号

项目不提供默认管理员或默认密码。请在部署后通过受控流程创建管理员，并使用随机密码和唯一 API 密钥。

## 📝 API文档

启动后端服务后，访问以下地址查看API文档：

```
http://localhost:8080/api/doc.html
```

## 🛠️ 技术特点

### 后端特点

1. **分层架构**：采用DDD领域驱动设计，清晰的分层结构
2. **安全认证**：基于JWT的无状态认证机制
3. **统一异常**：全局异常处理，友好的错误提示
4. **API文档**：集成Knife4j，自动生成接口文档
5. **代码规范**：遵循SOLID原则，代码简洁易维护

### 前端特点

1. **组件化**：基于Vue 3 Composition API
2. **状态管理**：使用Pinia进行状态管理
3. **UI组件**：Element Plus提供丰富的UI组件
4. **响应式**：现代化的响应式设计
5. **类型安全**：TypeScript支持（可选）

## 🎯 设计原则

本项目严格遵循以下设计原则：

- **KISS (Keep It Simple, Stupid)**：保持代码简单明了
- **YAGNI (You Aren't Gonna Need It)**：只实现当前需要的功能
- **SOLID**：面向对象设计的五大原则

## 📄 许可证

本项目仅供学习交流使用

## 👨‍💻 开发者

- AI Assistant
- 创建日期：2025-01-17

## 📮 联系方式

如有问题或建议，欢迎提Issue或Pull Request
