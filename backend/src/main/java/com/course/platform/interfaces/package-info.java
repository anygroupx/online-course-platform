/**
 * 接口层 (Interfaces Layer)
 * 
 * 职责：
 * - 处理外部请求（REST API、WebSocket等）
 * - 请求参数验证
 * - 响应格式转换
 * - 异常处理和统一响应
 * 
 * 包结构：
 * - api: REST控制器
 *   - admin: 管理端接口
 *   - user: 用户端接口
 *   - external: 对外开放API
 * - dto: 数据传输对象
 *   - request: 请求DTO
 *   - response: 响应DTO
 * - facade: 门面服务（聚合多个应用服务）
 * 
 * @author AI Assistant
 * @since 2025-12-22
 * @see com.course.platform.application 应用层
 */
package com.course.platform.interfaces;
