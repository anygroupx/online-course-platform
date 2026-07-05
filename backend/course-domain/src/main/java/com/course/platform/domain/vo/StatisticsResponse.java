package com.course.platform.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 统计数据响应VO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponse {

    /**
     * 总订单数
     */
    private Long totalOrders;

    /**
     * 今日订单数
     */
    private Long todayOrders;

    /**
     * 总用户数
     */
    private Long totalUsers;

    /**
     * 今日新增用户
     */
    private Long todayNewUsers;

    /**
     * 总交易额
     */
    private BigDecimal totalAmount;

    /**
     * 今日交易额
     */
    private BigDecimal todayAmount;

    /**
     * 代理总数
     */
    private Long totalAgents;

    /**
     * 今日登录代理数
     */
    private Long todayLoginAgents;

    /**
     * 待处理订单数
     */
    private Long pendingOrders;

    /**
     * 进行中订单数
     */
    private Long processingOrders;
}

