/**
 * 基础设施层 (Infrastructure Layer)
 * 
 * 职责：
 * - 领域层接口的具体实现
 * - 数据库访问（MyBatis Mapper）
 * - 外部服务集成
 * - 缓存、消息队列等技术组件
 * 
 * 包结构：
 * - persistence: 持久化实现（Repository实现、Mapper）
 * - external: 外部服务调用（HTTP客户端）
 * - config: 基础设施配置
 * - security: 安全实现
 * - cache: 缓存实现
 * 
 * @author AI Assistant
 * @since 2025-12-22
 * @see com.course.platform.domain 领域层接口定义
 */
package com.course.platform.infrastructure;
