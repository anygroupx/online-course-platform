/**
 * 应用层 (Application Layer)
 * 
 * 职责：
 * - 协调领域对象完成业务用例
 * - 事务管理和安全控制
 * - DTO与领域对象的转换
 * - 不包含业务规则，仅编排领域服务
 * 
 * 包结构：
 * - service: 应用服务，编排领域服务完成用例
 * - assembler: DTO装配器，负责对象转换
 * - event: 应用层事件处理
 * 
 * @author AI Assistant
 * @since 2025-12-22
 * @see com.course.platform.domain 领域层
 * @see com.course.platform.interfaces 接口层
 */
package com.course.platform.application;
