/**
 * 共享内核模块 (course-common)
 * 
 * 职责：
 * - 跨模块的纯技术基础设施
 * - 常量定义（纯技术常量，非业务常量）
 * - 通用异常基类
 * - 统一响应格式
 * 
 * 包结构：
 * - constant: 技术常量定义
 * - exception: 基础异常类
 * - result: 统一响应封装
 * 
 * ⚠️ 严格约束：
 * - 禁止引入 Spring、MyBatis 等框架依赖
 * - 禁止放置业务枚举、业务常量
 * - 业务相关的"共享"应放到对应领域的 domain 模块
 * 
 * @author AI Assistant
 * @since 2025-12-22
 */
package com.course.platform.common;
