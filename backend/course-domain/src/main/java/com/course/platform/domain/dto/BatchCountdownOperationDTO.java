package com.course.platform.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量倒计时操作请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于倒计时管理功能需求设计
 */
@Data
public class BatchCountdownOperationDTO {

    /**
     * 订单ID列表
     */
    private List<Long> orderIds;

    /**
     * 操作类型：complete-完成订单, adjust-调整倒计时
     */
    private String operationType;

    /**
     * 新的倒计时时长（分钟）- 仅调整倒计时时使用
     */
    private Integer newDuration;

    /**
     * 操作原因
     */
    private String reason;
}
