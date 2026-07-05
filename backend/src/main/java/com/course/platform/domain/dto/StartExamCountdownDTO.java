package com.course.platform.domain.dto;

import lombok.Data;

/**
 * 开始考试倒计时请求DTO
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于考试倒计时管理需求设计
 */
@Data
public class StartExamCountdownDTO {

    /**
     * 倒计时时长（分钟）
     */
    private Integer duration;

    /**
     * 操作原因
     */
    private String reason;
}
