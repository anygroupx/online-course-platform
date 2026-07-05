package com.course.platform.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量状态切换请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Data
public class BatchToggleStatusRequest {
    
    /**
     * 订单ID列表
     */
    private List<Long> orderIds;
    
    /**
     * 新状态
     */
    private Integer newStatus;
    
    /**
     * 倒计时时长（分钟）
     */
    private Integer countdownDuration;
    
    /**
     * 是否自动完成
     */
    private Boolean autoComplete;
    
    /**
     * 操作原因
     */
    private String reason;
}
