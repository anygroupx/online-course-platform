package com.course.platform.application.service.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.domain.dto.OrderCreateRequest;
import com.course.platform.domain.dto.OrderQueryRequest;
import com.course.platform.domain.entity.CourseOrder;

/**
 * 课程订单服务接口
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
public interface CourseOrderService {

    /**
     * 创建订单
     * 
     * @param request 订单创建请求
     * @param userId 用户ID
     * @return 订单ID
     */
    Long createOrder(OrderCreateRequest request, Long userId);

    /**
     * 分页查询订单
     * 
     * @param request 查询请求
     * @param userId 用户ID
     * @return 订单分页数据
     */
    IPage<CourseOrder> queryOrders(OrderQueryRequest request, Long userId);

    /**
     * 获取订单详情
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     * @return 订单详情
     */
    CourseOrder getOrderById(Long orderId, Long userId);

    /**
     * 取消订单
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     */
    void cancelOrder(Long orderId, Long userId);

    /**
     * 补单（重新提交）
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     */
    void retryOrder(Long orderId, Long userId);

    /**
     * 更新订单进度
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     */
    void updateOrderProgress(Long orderId, Long userId);

    /**
     * 根据订单号获取订单详情（带userId校验）
     * 
     * @param orderNo 订单编号
     * @param userId 用户ID（用于权限校验）
     * @return 订单详情
     */
    CourseOrder getOrderByOrderNo(String orderNo, Long userId);

    /**
     * 根据订单号取消订单（带userId校验）
     * 
     * @param orderNo 订单编号
     * @param userId 用户ID（用于权限校验）
     */
    void cancelOrderByOrderNo(String orderNo, Long userId);

    /**
     * 根据订单号补单（带userId校验）
     * 
     * @param orderNo 订单编号
     * @param userId 用户ID（用于权限校验）
     */
    void retryOrderByOrderNo(String orderNo, Long userId);

    /**
     * 根据订单号更新订单进度（带userId校验）
     * 
     * @param orderNo 订单编号
     * @param userId 用户ID（用于权限校验）
     */
    void updateOrderProgressByOrderNo(String orderNo, Long userId);
}

