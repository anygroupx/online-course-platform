package com.course.platform.application.service.payment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.domain.entity.PaymentOrder;

/**
 * 支付订单服务接口
 * 
 * @author AI Assistant
 * @date 2025-11-26
 */
public interface PaymentOrderService {

    /**
     * 根据订单号查询订单
     * 
     * @param orderNo 订单号
     * @return 支付订单
     */
    PaymentOrder getByOrderNo(String orderNo);

    /**
     * 根据订单号和用户ID查询订单
     * 
     * @param orderNo 订单号
     * @param userId 用户ID
     * @return 支付订单
     */
    PaymentOrder getByOrderNoAndUserId(String orderNo, Long userId);

    /**
     * 分页查询用户的支付订单
     * 
     * @param userId 用户ID
     * @param status 订单状态(可选)
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<PaymentOrder> getUserOrders(Long userId, String status, Integer pageNum, Integer pageSize);

    /**
     * 定时关闭超时订单
     */
    void closeTimeoutOrders();
}
