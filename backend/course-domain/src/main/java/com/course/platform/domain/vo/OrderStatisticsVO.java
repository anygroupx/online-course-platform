package com.course.platform.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单统计信息VO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class OrderStatisticsVO {

    /**
     * 总订单数
     */
    private Long totalOrders;

    /**
     * 待处理订单数
     */
    private Long pendingOrders;

    /**
     * 进行中订单数
     */
    private Long processingOrders;

    /**
     * 已完成订单数
     */
    private Long completedOrders;

    /**
     * 已取消订单数
     */
    private Long cancelledOrders;

    /**
     * 失败订单数
     */
    private Long failedOrders;

    /**
     * 总营收
     */
    private BigDecimal totalRevenue;

    /**
     * 今日订单数
     */
    private Long todayOrders;

    /**
     * 今日营收
     */
    private BigDecimal todayRevenue;

    /**
     * 平均订单金额
     */
    private BigDecimal averageOrderAmount;

    /**
     * 完成率
     */
    private String completionRate;

    /**
     * 取消率
     */
    private String cancellationRate;
}
