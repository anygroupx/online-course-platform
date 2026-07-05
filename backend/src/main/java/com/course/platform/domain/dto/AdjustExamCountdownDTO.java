package com.course.platform.domain.dto;

import lombok.Data;

/**
 * 调整考试倒计时请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于考试倒计时管理需求设计
 */
@Data
public class AdjustExamCountdownDTO {

    /**
     * 新的倒计时时长（分钟）
     */
    private Integer newDuration;

    /**
     * 调整原因
     */
    private String reason;
}
