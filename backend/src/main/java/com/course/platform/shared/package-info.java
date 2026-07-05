/**
 * 共享内核 (Shared Kernel)
 * 
 * 职责：
 * - 跨领域的通用组件
 * - 常量定义
 * - 通用异常
 * - 工具类
 * - 统一响应格式
 * 
 * 包结构：
 * - constant: 常量定义
 * - exception: 通用异常类
 * - util: 工具类
 * - result: 统一响应封装
 * 
 * 注意：
 * - 此层应保持最小化，避免过度使用
 * - 优先考虑将组件放入具体领域
 * 
 * @author AI Assistant
 * @since 2025-12-22
 */
package com.course.platform.shared;
