package com.course.platform.domain.dto;

import lombok.Data;

/**
 * 完成订单请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Data
public class CompleteOrderDTO {

    /**
     * 完成原因
     */
    private String reason;
}
