package com.course.platform.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 倒计时历史记录DTO
 * 包含账号和订单状态信息
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Data
public class CountdownHistoryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作前时长（分钟）
     */
    private Integer oldDuration;

    /**
     * 操作后时长（分钟）
     */
    private Integer newDuration;

    /**
     * 操作原因
     */
    private String reason;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;

    /**
     * 账号（用户名）
     */
    private String username;

    /**
     * 订单状态
     */
    private Integer orderStatus;

    /**
     * 订单状态文本
     */
    private String orderStatusText;
}



