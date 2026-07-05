package com.course.platform.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 切换订单状态请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Data
public class SwitchStatusDTO {

    /**
     * 新订单状态
     * 0-待处理, 1-进行中, 2-已完成, 3-已取消, 4-失败
     */
    @NotNull(message = "新订单状态不能为空")
    private Integer newStatus;

    /**
     * 操作原因
     */
    private String reason;
}
